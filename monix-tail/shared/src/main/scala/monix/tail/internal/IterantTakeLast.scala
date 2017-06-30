/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.execution.internal.collection.DropHeadOnOverflowQueue
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.Iterant
import monix.types.Monad
import monix.types.syntax._

private[tail] object IterantTakeLast {
  /**
    * Implementation for `Iterant#takeLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Monad[F]): Iterant[F, A] = {
    import F.{functor, applicative => A}

    def finalCursor(buffer: DropHeadOnOverflowQueue[A]): F[Iterant[F, A]] = {
      val cursor = BatchCursor.fromIterator(buffer.iterator(true), Int.MaxValue)
      A.pure(NextCursor(cursor, A.pure(Halt(None)), A.unit))
    }

    def loop(buffer: DropHeadOnOverflowQueue[A])(source: Iterant[F, A]): F[Iterant[F, A]] = {
      source match {
        case Next(item, rest, stop) =>
          buffer.offer(item)
          rest.flatMap(loop(buffer))
        case NextCursor(cursor, rest, stop) =>
          while (cursor.hasNext()) buffer.offer(cursor.next())
          rest.flatMap(loop(buffer))
        case NextBatch(batch, rest, stop) =>
          val cursor = batch.cursor()
          while (cursor.hasNext()) buffer.offer(cursor.next())
          rest.flatMap(loop(buffer))
        case Suspend(rest, stop) =>
          rest.flatMap(loop(buffer))
        case Last(item) =>
          buffer.offer(item)
          finalCursor(buffer)
        case Halt(None) =>
          finalCursor(buffer)
        case halt @ Halt(Some(_)) =>
          A.pure(halt)
      }
    }

    // Current earlyStop has to be preserved
    val stopRef = source.earlyStop
    if (n < 1)
      Suspend(stopRef.map(_ => Halt(None)), stopRef)
    else {
      // Suspending execution, because pushing into our buffer
      // is side-effecting
      val buffer = A.eval(DropHeadOnOverflowQueue.boxed[A](n))
      Suspend(buffer.flatMap(b => loop(b)(source)), stopRef)
    }
  }
}
