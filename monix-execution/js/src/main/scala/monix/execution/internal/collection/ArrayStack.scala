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

package monix.execution.internal.collection

import monix.execution.internal.math
import scala.scalajs.js

/** Provides a fast platform-specific array-based stack.
  *
  * INTERNAL API.
  */
private[monix] final class ArrayStack[A] private (
  arrayRef: js.Array[AnyRef],
  private[this] val minCapacity: Int,
  private[this] var index: Int)
  extends Serializable with Cloneable {

  private[this] val array =
    if (arrayRef ne null) arrayRef
    else new js.Array[AnyRef](minCapacity)

  private[this] var capacity = array.length
  private[this] var popAtCapacity = capacity >> 2

  /** Default constructor. */
  def this() = this(null, 8, 0)
  def this(minCapacity: Int) =
    this(null, math.nextPowerOf2(minCapacity), 0)

  /** Returns `true` if the stack is empty. */
  def isEmpty: Boolean = index == 0

  /** Returns the size of our stack. */
  def size: Int = index

  /** Returns the current capacity of the internal array, which
    * grows and shrinks in response to `push` and `pop` operations.
    */
  def currentCapacity: Int = capacity

  /** Returns the minimum capacity of the internal array. */
  def minimumCapacity: Int = minCapacity

  /** Pushes an item on the stack. */
  def push(a: A): Unit = {
    // If over capacity, we must double the array size!
    if (index == capacity) {
      capacity = capacity << 1 // * 2
      popAtCapacity = capacity >> 2 // div 4
      array.length = capacity
    }
    array(index) = a.asInstanceOf[AnyRef]
    index += 1
  }

  /** Pushes an entire iterator on the stack. */
  def pushAll(cursor: Iterator[A]): Unit = {
    while (cursor.hasNext) push(cursor.next())
  }

  /** Pushes an entire sequence on the stack. */
  def pushAll(seq: Iterable[A]): Unit = {
    pushAll(seq.iterator)
  }

  /** Pushes another `ArrayStack` on this stack. */
  def pushAll(stack: ArrayStack[A]): Unit = {
    pushAll(stack.iterator)
  }

  /** Pops an item from the stack (in LIFO order).
    *
    * Returns `null` in case the stack is empty.
    */
  def pop(): A = {
    if (index == 0) return null.asInstanceOf[A]
    index -= 1
    val result = array(index)

    // Shrinks array if only a quarter of it is full
    if (index == popAtCapacity && capacity > minCapacity) {
      capacity = capacity >> 1
      popAtCapacity = capacity >> 2
      array.length = capacity
    }

    result.asInstanceOf[A]
  }

  /** Returns a shallow copy of this stack. */
  override def clone(): ArrayStack[A] = {
    val copy = array.jsSlice(0, array.length)
    new ArrayStack[A](copy, minCapacity, index)
  }

  /** Returns an iterator that can traverse the whole stack. */
  def iterator: Iterator[A] =
    new Iterator[A] {
      private[this] var i = 0
      def hasNext: Boolean = i < index

      def next(): A = {
        val elem = array(i)
        i += 1
        elem.asInstanceOf[A]
      }
    }
}
