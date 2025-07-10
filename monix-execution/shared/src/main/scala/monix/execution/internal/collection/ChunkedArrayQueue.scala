/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import java.util.concurrent.atomic.AtomicReferenceArray

private[monix] final class ChunkedArrayQueue[A] private(
  initialTailArray: AtomicReferenceArray[AnyRef],
  initialTailIndex: Int,
  initialHeadArray: AtomicReferenceArray[AnyRef],
  initialHeadIndex: Int,
  chunkSize: Int,
) extends Serializable { self =>

  assert(chunkSize > 1, "chunkSize > 1")

  private val modulo = chunkSize - 1
  private val lastElementIndex = chunkSize - 2
  private var tailArray = initialTailArray
  private var tailIndex = initialTailIndex
  private var headArray = initialHeadArray
  private var headIndex = initialHeadIndex

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
    tailArray.set(tailIndex, a.asInstanceOf[AnyRef])

    if (tailIndex == lastElementIndex) {
      val newArray = new AtomicReferenceArray[AnyRef](chunkSize)
      tailArray.set(modulo, newArray)
      tailArray = newArray
      tailIndex = 0
    } else {
      tailIndex += 1
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
      val result = headArray.getAndSet(headIndex, null).asInstanceOf[A]

      if (headIndex == lastElementIndex) {
        headArray = headArray.get(modulo).asInstanceOf[AtomicReferenceArray[AnyRef]]
        headIndex = 0
      } else {
        headIndex += 1
      }
      result
    } else {
      null.asInstanceOf[A]
    }
  }

  /** Builds an iterator out of this queue. */
  def iterator: Iterator[A] =
    new Iterator[A] {
      private var headArray = self.headArray
      private var headIndex = self.headIndex
      private val tailArray = self.tailArray
      private val tailIndex = self.tailIndex

      def hasNext: Boolean = {
        (headArray ne tailArray) || headIndex < tailIndex
      }

      def next(): A = {
        val result = headArray.get(headIndex).asInstanceOf[A]

        if (headIndex == lastElementIndex) {
          headArray = headArray.get(modulo).asInstanceOf[AtomicReferenceArray[AnyRef]]
          headIndex = 0
        } else {
          headIndex += 1
        }
        result
      }
    }

  def shallowCopy(): ChunkedArrayQueue[A] =
    new ChunkedArrayQueue[A](tailArray, tailIndex, headArray, headIndex, chunkSize)
}

private[monix] object ChunkedArrayQueue {
  /**
   * Builds a new [[ChunkedArrayQueue]].
   */
  def apply[A](chunkSize: Int = 8): ChunkedArrayQueue[A] = {
    val arr = new AtomicReferenceArray[AnyRef](chunkSize)
    new ChunkedArrayQueue[A](arr, 0, arr, 0, chunkSize)
  }
}
