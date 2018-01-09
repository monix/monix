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
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.{Batch, BatchCursor}

import scala.collection.mutable

private[tail] object IterantDropLast {
  /**
    * Implementation for `Iterant#dropLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {

    def processCursor(toDrop: Int, length: Int, queue: mutable.Queue[A],
                      cursor: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] = {

      val limit = cursor.recommendedBatchSize
      var queueLength = length
      val buffer = mutable.Buffer[A]()
      var bufferLength = 0

      while (cursor.hasNext()) {
        queue.enqueue(cursor.next())
        if (queueLength >= toDrop && bufferLength < limit) {
          buffer.append(queue.dequeue())
          bufferLength += 1
        }
        else queueLength += 1
      }

      if (bufferLength > 0)
        NextBatch(Batch.fromSeq(buffer), rest.map(loop(toDrop, queueLength, queue)), stop)
      else
        Suspend(rest.map(loop(toDrop, queueLength, queue)), stop)
    }

    def finalCursor(toDrop: Int, length: Int, queue: mutable.Queue[A]): Iterant[F, A] = {
      var queueLength = length
      val buffer = mutable.Buffer[A]()

      while (queueLength > toDrop) {
        buffer.append(queue.dequeue())
        queueLength -= 1
      }
      val cursor = BatchCursor.fromSeq(buffer)
      NextCursor(cursor, F.pure(Halt(None)), F.unit)
    }

    def loop(toDrop: Int, length: Int, queue: mutable.Queue[A])
            (source: Iterant[F, A]): Iterant[F, A] = {
      try if (toDrop <= 0) source else source match {
        case Next(elem, rest, stop) =>
          queue.enqueue(elem)
          if (length >= toDrop)
            Next(queue.dequeue(), rest.map(loop(toDrop, length, queue)), stop)
          else
            Suspend(rest.map(loop(toDrop, length + 1, queue)), stop)
        case NextCursor(cursor, rest, stop) =>
          processCursor(toDrop, length, queue, cursor, rest, stop)
        case NextBatch(batch, rest, stop) =>
          processCursor(toDrop, length, queue, batch.cursor(), rest, stop)
        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(toDrop, length, queue)), stop)
        case Last(item) =>
          queue.enqueue(item)
          finalCursor(toDrop, length + 1, queue)
        case Halt(None) =>
          finalCursor(toDrop, length, queue)
        case halt@Halt(Some(_)) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          val stop = source.earlyStop
          Suspend(stop.map(_ => Halt(Some(ex))), stop)
      }
    }

    source match {
      // Suspending execution, because pushing into our queue
      // is side-effecting
      case _ =>
        Suspend(F.delay(loop(n, 0, mutable.Queue.empty[A])(source)), source.earlyStop)
    }
  }
}