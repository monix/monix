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
import monix.execution.internal.collection.DropHeadOnOverflowQueue
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor

private[tail] object IterantTakeLast {
  /**
    * Implementation for `Iterant#takeLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {

    def finalCursor(buffer: DropHeadOnOverflowQueue[A]): F[Iterant[F, A]] = {
      val cursor = BatchCursor.fromIterator(buffer.iterator(true), Int.MaxValue)
      F.pure(NextCursor(cursor, F.pure(Halt(None)), F.unit))
    }

    def loop(buffer: DropHeadOnOverflowQueue[A])(source: Iterant[F, A]): F[Iterant[F, A]] = {
      source match {
        case Next(item, rest, _) =>
          buffer.offer(item)
          rest.flatMap(loop(buffer))
        case NextCursor(cursor, rest, _) =>
          while (cursor.hasNext()) buffer.offer(cursor.next())
          rest.flatMap(loop(buffer))
        case NextBatch(batch, rest, _) =>
          val cursor = batch.cursor()
          while (cursor.hasNext()) buffer.offer(cursor.next())
          rest.flatMap(loop(buffer))
        case Suspend(rest, _) =>
          rest.flatMap(loop(buffer))
        case Last(item) =>
          buffer.offer(item)
          finalCursor(buffer)
        case Halt(None) =>
          finalCursor(buffer)
        case halt @ Halt(Some(_)) =>
          F.pure(halt)
      }
    }

    // Current earlyStop has to be preserved
    val stopRef = source.earlyStop
    if (n < 1)
      Suspend(stopRef.map(_ => Halt(None)), stopRef)
    else {
      // Suspending execution, because pushing into our buffer
      // is side-effecting
      val buffer = F.delay(DropHeadOnOverflowQueue.boxed[A](n))
      Suspend(buffer.flatMap(b => loop(b)(source)), stopRef)
    }
  }
}
