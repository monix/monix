/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.tail.internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor
import monix.tail.internal.IterantUtils._

private[tail] object IterantScanEval {
  /**
    * Implementation for `Iterant#scanEval`
    */
  def apply[F[_], A, S](source: Iterant[F, A], seed: F[S], ff: (S, A) => F[S])
    (implicit F: Sync[F]): Iterant[F, S] = {

    def protectedF(s: S, a: A, rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, S] = {
      val next = ff(s, a)
        .map(s => nextS(s, rest.map(loop(s)), stop))
        .handleErrorWith(signalError[F, S](stop))

      Suspend(next, stop)
    }

    def evalNextCursor(state: S, ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext)
        Suspend[F, S](rest.map(loop(state)), stop)
      else {
        val head = cursor.next()
        val tail = if (cursor.hasNext()) F.pure(ref: Iterant[F, A]) else rest
        protectedF(state, head, tail, stop)
      }
    }

    def loop(state: S)(source: Iterant[F, A]): Iterant[F, S] =
      try source match {
        case Next(head, tail, stop) =>
          protectedF(state, head, tail, stop)

        case ref @ NextCursor(cursor, rest, stop) =>
          evalNextCursor(state, ref, cursor, rest, stop)

        case NextBatch(gen, rest, stop) =>
          val cursor = gen.cursor()
          val ref = NextCursor(cursor, rest, stop)
          evalNextCursor(state, ref, cursor, rest, stop)

        case Suspend(rest, stop) =>
          Suspend[F,S](rest.map(loop(state)), stop)

        case Last(item) =>
          val fa = ff(state, item)
          Suspend(fa.map(s => lastS[F,S](s)), F.unit)

        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, S]]
      } catch {
        case NonFatal(ex) => signalError(source, ex)
      }

    // Suspending execution in order to not trigger
    // side-effects accidentally
    val process = F.suspend(
      seed.attempt.map {
        case Left(e) =>
          signalError[F, A, S](source, e)
        case Right(initial) =>
          loop(initial)(source)
      })

    Suspend(process, source.earlyStop)
  }
}
