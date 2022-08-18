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

package monix.execution.internal
package collection

private[monix] final class ChunkedArrayQueue[A] private (
  initialTailArray: Array[AnyRef],
  initialTailIndex: Int,
  initialHeadArray: Array[AnyRef],
  initialHeadIndex: Int,
  chunkSize: Int
) extends Serializable { self =>

  assert(chunkSize > 1, "chunkSize > 1")

  private[this] val modulo = chunkSize - 1
  private[this] var tailArray = initialTailArray
  private[this] var tailIndex = initialTailIndex
  private[this] var headArray = initialHeadArray
  private[this] var headIndex = initialHeadIndex

  /**
    * Returns `true` if the queue is empty, `false` otherwise.
    */
  def isEmpty: Boolean = {
    (headArray eq tailArray) && headIndex == tailIndex
  }

  /**
    * Enqueues an item on the queue.
    */
  def enqueue(a: A): Unit = {
    tailArray(tailIndex) = a.asInstanceOf[AnyRef]
    tailIndex += 1

    if (tailIndex == modulo) {
      val newArray = new Array[AnyRef](chunkSize)
      tailArray(tailIndex) = newArray
      tailArray = newArray
      tailIndex = 0
    }
  }

  /** Pushes an entire iterator on the stack. */
  def enqueueAll(cursor: Iterator[A]): Unit = {
    while (cursor.hasNext) enqueue(cursor.next())
  }

  /** Pushes an entire sequence on the stack. */
  def enqueueAll(seq: Iterable[A]): Unit = {
    enqueueAll(seq.iterator)
  }

  /** Pushes an entire sequence on the stack. */
  def enqueueAll(stack: ChunkedArrayQueue[A]): Unit =
    enqueueAll(stack.iterator)

  /**
    * Pops an item from the queue, FIFO order.
    */
  def dequeue(): A = {
    if ((headArray ne tailArray) || headIndex < tailIndex) {
      val result = headArray(headIndex).asInstanceOf[A]
      headArray(headIndex) = null
      headIndex += 1

      if (headIndex == modulo) {
        headArray = headArray(modulo).asInstanceOf[Array[AnyRef]]
        headIndex = 0
      }
      result
    } else {
      null.asInstanceOf[A]
    }
  }

  /** Builds an iterator out of this queue. */
  def iterator: Iterator[A] =
    new Iterator[A] {
      private[this] var headArray = self.headArray
      private[this] var headIndex = self.headIndex
      private[this] val tailArray = self.tailArray
      private[this] val tailIndex = self.tailIndex

      def hasNext: Boolean = {
        (headArray ne tailArray) || headIndex < tailIndex
      }

      def next(): A = {
        val result = headArray(headIndex).asInstanceOf[A]
        headIndex += 1

        if (headIndex == modulo) {
          headArray = headArray(modulo).asInstanceOf[Array[AnyRef]]
          headIndex = 0
        }
        result
      }
    }
}

private[monix] object ChunkedArrayQueue {
  /**
    * Builds a new [[ChunkedArrayQueue]].
    */
  def apply[A](chunkSize: Int = 8): ChunkedArrayQueue[A] = {
    val arr = new Array[AnyRef](chunkSize)
    new ChunkedArrayQueue[A](arr, 0, arr, 0, chunkSize)
  }
}
