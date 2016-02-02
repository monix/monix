/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.execution.internal.collection

import monix.execution.internal.collection.UnlimitedBuffer.Node
import monix.execution.internal.math.nextPowerOf2
import scala.reflect.ClassTag

private[monix]
final class UnlimitedBuffer[T : ClassTag] private (initialCapacity: Int)
  extends Buffer[T] {

  private[this] var bufferHead = new Node(new Array[T](initialCapacity), next = null)
  private[this] var bufferTail = bufferHead
  private[this] var bufferTailArray = bufferHead.array
  private[this] var bufferTailIdx = 0
  private[this] var tailCapacity = initialCapacity
  private[this] var bufferSize = 0

  def offer(elem: T): Int = {
    bufferTailArray(bufferTailIdx) = elem
    bufferTailIdx += 1
    bufferSize += 1

    // time to allocate new node?
    if (bufferTailIdx == tailCapacity) {
      bufferTailIdx = 0
      tailCapacity = tailCapacity * 2
      val newNode = new Node(new Array[T](tailCapacity), next = null)
      bufferTail.next = newNode
      bufferTailArray = newNode.array
      bufferTail = newNode
    }

    0
  }

  def offerMany(seq: T*): Long = {
    val i = seq.iterator
    while (i.hasNext) offer(i.next())
    0
  }

  def clear(): Unit = {
    bufferHead = new Node(new Array[T](initialCapacity), next = null)
    bufferTail = bufferHead
    bufferTailArray = bufferHead.array
    bufferTailIdx = 0
    tailCapacity = initialCapacity
    bufferSize = 0
  }

  def length: Int = {
    bufferSize
  }

  def iterator: Iterator[T] = {
    new Iterator[T] {
      private[this] var currentNode = bufferHead
      private[this] var currentArray = currentNode.array
      private[this] var idx = 0

      def hasNext: Boolean = {
        currentNode != null && (currentNode != bufferTail || idx != bufferTailIdx)
      }

      def next(): T = {
        if (!hasNext) throw new NoSuchElementException("UnlimitedBuffer.iterator.next")
        val result = currentArray(idx)
        idx += 1

        if (idx == currentArray.length) {
          idx = 0
          currentNode = currentNode.next
          currentArray = currentNode.array
        }

        result
      }
    }
  }
}

private[monix] object UnlimitedBuffer {
  def apply[T : ClassTag](): UnlimitedBuffer[T] =
    new UnlimitedBuffer[T](16)

  def apply[T : ClassTag](minInitialCapacity: Int): UnlimitedBuffer[T] =
    new UnlimitedBuffer[T](nextPowerOf2(minInitialCapacity))

  private final class Node[T](val array: Array[T], var next: Node[T])
}