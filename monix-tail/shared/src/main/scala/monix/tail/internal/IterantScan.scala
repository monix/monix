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

package monix.tail
package internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor
import monix.tail.internal.IterantUtils._
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantScan {
  /** Implementation for `Iterant#scan`. */
  def apply[F[_], A, S](fa: Iterant[F, A], initial: => S, f: (S, A) => S)
    (implicit F: Sync[F]): Iterant[F, S] = {

    def processCursor(state: S, cursor: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext())
        Suspend(rest.map(loop(state)), stop)
      else if (cursor.recommendedBatchSize <= 1) {
        val newState = f(state, cursor.next())
        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest, stop))
          else rest

        Next(newState, next.map(loop(newState)), stop)
      } else {
        val buffer = ArrayBuffer.empty[S]
        var toProcess = cursor.recommendedBatchSize
        var newState = state

        while (toProcess > 0 && cursor.hasNext()) {
          newState = f(newState, cursor.next())
          buffer += newState
          toProcess -= 1
        }

        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest, stop))
          else rest

        val elems = BatchCursor.fromAnyArray[S](buffer.toArray[Any])
        NextCursor(elems, next.map(loop(newState)), stop)
      }
    }

    def loop(state: S)(fa: Iterant[F, A]): Iterant[F, S] =
      try fa match {
        case Next(a, rest, stop) =>
          val newState = f(state, a)
          Next(newState, rest.map(loop(newState)), stop)

        case NextCursor(cursor, rest, stop) =>
          processCursor(state, cursor, rest, stop)

        case NextBatch(batch, rest, stop) =>
          val cursor = batch.cursor()
          processCursor(state, cursor, rest, stop)

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(state)), stop)

        case Last(a) =>
          Last(f(state, a))

        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, S]]

      } catch {
        case NonFatal(e) =>
          signalError(fa, e)
      }

    // Given that `initial` is a by-name value, we have
    // to suspend
    val task = F.delay {
      try loop(initial)(fa)
      catch { case NonFatal(e) => Halt[F, S](Some(e)) }
    }
    Suspend(task, F.unit)
  }
}
