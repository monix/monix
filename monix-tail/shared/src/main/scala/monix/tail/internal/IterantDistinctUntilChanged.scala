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

import cats.Eq
import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor
import monix.tail.internal.IterantUtils.signalError
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantDistinctUntilChanged {
  /** Implementation for `distinctUntilChangedByKey`. */
  def apply[F[_], A, K](self: Iterant[F, A], f: A => K)
    (implicit F: Sync[F], K: Eq[K]): Iterant[F, A] = {

    def processCursor(prev: K, self: NextCursor[F, A]) = {
      val NextCursor(cursor, rest, stop) = self

      if (!cursor.hasNext()) {
        Suspend(rest.map(loop(prev)), stop)
      }
      else if (cursor.recommendedBatchSize <= 1) {
        val a = cursor.next()
        val k = f(a)
        if (K.neqv(prev, k)) Next(a, F.delay(loop(k)(self)), stop)
        else Suspend(F.delay(loop(prev)(self)), stop)
      }
      else {
        val buffer = ArrayBuffer.empty[A]
        var count = cursor.recommendedBatchSize
        var current: K = prev

        // We already know hasNext == true
        do {
          val a = cursor.next()
          val k = f(a)
          count -= 1

          if (K.neqv(current, k)) {
            current = k
            buffer += a
          }
        } while (count > 0 && cursor.hasNext())

        val next =
          if (cursor.hasNext())
            F.delay(loop(current)(self))
          else
            rest.map(loop(current))

        if (buffer.isEmpty)
          Suspend(next, stop)
        else {
          val ref = BatchCursor.fromAnyArray[A](buffer.toArray[Any])
          NextCursor(ref, next, stop)
        }
      }
    }

    def loop(prev: K)(self: Iterant[F, A]): Iterant[F, A] = {
      try self match {
        case Next(a, rest, stop) =>
          val k = f(a)
          if (K.neqv(prev, k)) Next(a, rest.map(loop(k)), stop)
          else Suspend(rest.map(loop(prev)), stop)

        case node @ NextCursor(_, _, _) =>
          processCursor(prev, node)
        case NextBatch(ref, rest, stop) =>
          processCursor(prev, NextCursor(ref.cursor(), rest, stop))

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(prev)), stop)
        case Last(a) =>
          if (K.neqv(prev, f(a))) self else Halt(None)
        case Halt(_) =>
          self
      } catch {
        case e if NonFatal(e) =>
          signalError(self, e)
      }
    }

    // Starting function, needed because we don't have a start
    // and I'd hate to use `null` for `prev` or box it in `Option`
    def start(self: Iterant[F, A]): Iterant[F, A] = {
      try self match {
        case Next(a, rest, stop) =>
          Next(a, rest.map(loop(f(a))), stop)
        case node @ NextCursor(ref, rest, stop) =>
          if (!ref.hasNext())
            Suspend(rest.map(start), stop)
          else {
            val a = ref.next()
            val k = f(a)
            Next(a, F.delay(loop(k)(node)), stop)
          }
        case NextBatch(ref, rest, stop) =>
          start(NextCursor(ref.cursor(), rest, stop))
        case Suspend(rest, stop) =>
          Suspend(rest.map(start), stop)
        case Last(_) | Halt(_) =>
          self
      } catch {
        case e if NonFatal(e) =>
          signalError(self, e)
      }
    }

    self match {
      case Suspend(_, _) | Halt(_) =>
        // Fast-path
        start(self)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(start(self)), self.earlyStop)
    }
  }
}
