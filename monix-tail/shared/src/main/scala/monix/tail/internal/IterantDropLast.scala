/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable

private[tail] object IterantDropLast {
  /**
    * Implementation for `Iterant#dropLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {
    if (n <= 0) source
    else Suspend(F.delay(new Loop(n).apply(source)))
  }

  private final class Loop[F[_], A](n: Int)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var queue = Queue[A]()
    private[this] var length = 0

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      queue = queue.enqueue(ref.item)
      if (length >= n) {
        val (nextItem, nextQueue) = queue.dequeue
        queue = nextQueue
        Next(nextItem, ref.rest.map(this))
      } else {
        length += 1
        Suspend(ref.rest.map(this))
      }
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val limit = cursor.recommendedBatchSize
      val buffer = mutable.Buffer[A]()

      @tailrec
      def queueLoop(queue: Queue[A], toProcess: Int): Queue[A] = {
        if (!(toProcess > 0 && cursor.hasNext()))
          queue
        else {
          val updatedQueue = queue.enqueue(cursor.next())
          if (length >= n) {
            val (item, dequeuedQueue) = updatedQueue.dequeue
            buffer.append(item)
            queueLoop(dequeuedQueue, toProcess - 1)
          } else {
            length += 1
            queueLoop(updatedQueue, toProcess - 1)
          }
        }
      }

      queue = queueLoop(queue, limit)
      val next: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else rest
      NextCursor(BatchCursor.fromSeq(buffer.toSeq), next.map(this))
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      queue = queue.enqueue(ref.item)
      if (length >= n) {
        val (nextItem, nextQueue) = queue.dequeue
        queue = nextQueue
        Last(nextItem)
      } else {
        length += 1
        Halt(None)
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}
