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
import scala.util.control.NonFatal

import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable

import monix.execution.internal.collection.ArrayStack

private[tail] object IterantDropLast {
  /**
    * Implementation for `Iterant#dropLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {
    if (n <= 0) source
    else Suspend(F.delay(new Loop(n).apply(source)))
  }

  class Loop[F[_], A](n: Int)(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A])
  {
    private[this] val stack = new ArrayStack[F[Iterant[F, A]]]()
    private[this] var queue = Queue[A]()
    private[this] var length = 0

    def apply(source: Iterant[F, A]): Iterant[F, A] =
      try source match {
        case Next(item, rest) =>
          queue = queue.enqueue(item)
          if (length >= n) {
            val (nextItem, nextQueue) = queue.dequeue
            queue = nextQueue
            Next(nextItem, rest.map(this))
          }
          else {
            length += 1
            Suspend(rest.map(this))
          }

        case ref@NextCursor(_, _) =>
          processCursor(ref)

        case NextBatch(batch, rest) =>
          processCursor(NextCursor(batch.cursor(), rest))

        case Suspend(rest) =>
          Suspend(rest.map(this))

        case s @ Scope(_, _, _) =>
          s.runMap(this)

        case Concat(lh, rh) =>
          stack.push(rh)
          Suspend(lh.map(this))

        case Last(item) =>
          stack.pop() match {
            case null =>
              queue = queue.enqueue(item)
              finalCursor(length + 1)
            case some =>
              this(Next(item, some))
          }

        case Halt(None) =>
          stack.pop() match {
            case null => finalCursor(length)
            case some => Suspend(some.map(this))
          }

        case halt@Halt(Some(_)) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          Iterant.raiseError(ex)
      }

    def processCursor(ref: NextCursor[F, A]): Iterant[F, A] = {
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
      NextCursor(BatchCursor.fromSeq(buffer), next.map(this))
    }

    def finalCursor(length: Int): Iterant[F, A] = {
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
      NextCursor(BatchCursor.fromSeq(buffer), F.pure(Iterant.empty))
    }
  }
}