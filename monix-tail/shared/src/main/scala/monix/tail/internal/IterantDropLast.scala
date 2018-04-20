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
import monix.tail.batches.BatchCursor

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable

private[tail] object IterantDropLast {
  /**
    * Implementation for `Iterant#dropLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {

    def processCursor(length: Int, queue: Queue[A], ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest, stop) = ref
      val limit = cursor.recommendedBatchSize

      val buffer = mutable.Buffer[A]()
      var queueLength = length

      @tailrec
      def queueLoop(queue: Queue[A], toProcess: Int): Queue[A] = {
        if (!(toProcess > 0 && cursor.hasNext()))
          queue
        else {
          val updatedQueue = queue.enqueue(cursor.next())
          if (queueLength >= n) {
            val (item, dequeuedQueue) = updatedQueue.dequeue
            buffer.append(item)
            queueLoop(dequeuedQueue, toProcess - 1)
          } else {
            queueLength += 1
            queueLoop(updatedQueue, toProcess - 1)
          }
        }
      }

      val finalQueue = queueLoop(queue, limit)
      val next: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else rest
      NextCursor(BatchCursor.fromSeq(buffer), next.map(loop(queueLength, finalQueue)), stop)
    }

    def finalCursor(length: Int, queue: Queue[A]): Iterant[F, A] = {
      val buffer = mutable.Buffer[A]()

      @tailrec
      def queueLoop(queue: Queue[A], queueLength: Int): Queue[A] = {
        if (queueLength <= n) {
          queue
        } else {
          val (item, nextQueue) = queue.dequeue
          buffer.append(item)
          queueLoop(nextQueue, queueLength - 1)
        }
      }

      queueLoop(queue, length)
      NextCursor(BatchCursor.fromSeq(buffer), F.pure(Iterant.empty), F.unit)
    }

    def loop(length: Int, queue: Queue[A])(source: Iterant[F, A]): Iterant[F, A] = {
      try if (n <= 0) source else source match {
        case Next(item, rest, stop) =>
          val updatedQueue = queue.enqueue(item)
          if (length >= n) {
            val (nextItem, dequeuedQueue) = updatedQueue.dequeue
            Next(nextItem, rest.map(loop(length, dequeuedQueue)), stop)
          }
          else Suspend(rest.map(loop(length + 1, updatedQueue)), stop)

        case ref@NextCursor(_, _, _) =>
          processCursor(length, queue, ref)

        case NextBatch(batch, rest, stop) =>
          processCursor(length, queue, NextCursor(batch.cursor(), rest, stop))

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(length, queue)), stop)

        case Last(item) =>
          finalCursor(length + 1, queue.enqueue(item))

        case Halt(None) =>
          finalCursor(length, queue)

        case halt@Halt(Some(_)) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          val stop = source.earlyStop
          Suspend(stop.map(_ => Halt(Some(ex))), stop)
      }
    }

    source match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        // We can have side-effects with NextBatch/NextCursor
        // processing, so suspending execution in this case
        Suspend(F.delay(loop(0, Queue.empty[A])(source)), source.earlyStop)
      case _ =>
        loop(0, Queue.empty[A])(source)
    }
  }
}