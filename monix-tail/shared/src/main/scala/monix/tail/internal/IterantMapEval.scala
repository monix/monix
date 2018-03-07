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

private[tail] object IterantMapEval {
  /**
    * Implementation for `Iterant#mapEval`
    */
  def apply[F[_], A, B](source: Iterant[F, A], ff: A => F[B])
    (implicit F: Sync[F]): Iterant[F, B] = {

    def evalNextCursor(ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext) {
        Suspend[F, B](rest.map(loop), stop)
      } else {
        val head = cursor.next()
        val fb = try ff(head) catch { case NonFatal(e) => F.raiseError[B](e) }
        // If the iterator is empty, then we can skip a beat
        val tail = if (cursor.hasNext()) F.pure(ref: Iterant[F, A]) else rest
        val suspended = fb.map(h => nextS(h, tail.map(loop), stop))
        Suspend[F, B](suspended, stop)
      }
    }

    def loop(source: Iterant[F, A]): Iterant[F, B] =
      try source match {
        case Next(head, tail, stop) =>
          val rest = ff(head).map(h => nextS(h, tail.map(loop), stop))
          Suspend(rest, stop)
        case ref @ NextCursor(cursor, rest, stop) =>
          evalNextCursor(ref, cursor, rest, stop)
        case NextBatch(gen, rest, stop) =>
          val cursor = gen.cursor()
          val ref = NextCursor(cursor, rest, stop)
          evalNextCursor(ref, cursor, rest, stop)
        case Suspend(rest, stop) =>
          Suspend[F,B](rest.map(loop), stop)
        case Last(item) =>
          Suspend(ff(item).map(h => lastS[F,B](h)), F.unit)
        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, B]]
      } catch {
        case ex if NonFatal(ex) =>
          signalError(source, ex)
      }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)), source.earlyStop)
    }
  }
}
