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

import scala.util.control.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantScan {
  /** Implementation for `Iterant#scan`. */
  def apply[F[_], A, S](fa: Iterant[F, A], initial: => S, f: (S, A) => S)
    (implicit F: Sync[F]): Iterant[F, S] = {

    def processCursor(state: S, cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext())
        Suspend(rest.map(loop(state)))
      else if (cursor.recommendedBatchSize <= 1) {
        val newState = f(state, cursor.next())
        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest))
          else rest

        Next(newState, next.map(loop(newState)))
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
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest))
          else rest

        val elems = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[S]]
        NextCursor(elems, next.map(loop(newState)))
      }
    }

    def loop(state: S)(fa: Iterant[F, A]): Iterant[F, S] =
      try fa match {
        case s @ Scope(_, _, _) =>
          s.runMap(loop(state))

        case Next(a, rest) =>
          val newState = f(state, a)
          Next(newState, rest.map(loop(newState)))

        case NextCursor(cursor, rest) =>
          processCursor(state, cursor, rest)

        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          processCursor(state, cursor, rest)

        case Suspend(rest) =>
          Suspend(rest.map(loop(state)))

        case Last(a) =>
          Last(f(state, a))

        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, S]]

      } catch {
        case e if NonFatal(e) =>
          Iterant.raiseError(e)
      }

    // Given that `initial` is a by-name value, we have
    // to suspend
    val task = F.delay {
      try loop(initial)(fa)
      catch { case e if NonFatal(e) => Halt[F, S](Some(e)) }
    }
    Suspend(task)
  }
}
