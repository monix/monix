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

/** Provides a fast platform-specific array-based stack.
  *
  * INTERNAL API.
  */
private[monix] final class ArrayStack[A] private (
  arrayRef: Array[AnyRef],
  private[this] val minCapacity: Int,
  private[this] var index: Int)
  extends Serializable with Cloneable {

  private[this] var array =
    if (arrayRef ne null) arrayRef
    else new Array[AnyRef](minCapacity)

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
      val newCapacity = capacity << 1 // * 2
      val copy = new Array[AnyRef](newCapacity)
      System.arraycopy(array, 0, copy, 0, index)
      // Mutating internal state
      capacity = newCapacity
      popAtCapacity = capacity >> 2
      array = copy
    }

    array(index) = a.asInstanceOf[AnyRef]
    index += 1
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
    if (index == popAtCapacity && capacity != minCapacity) {
      val newCapacity = capacity >> 1
      val copy = new Array[AnyRef](newCapacity)
      System.arraycopy(array, 0, copy, 0, index)
      // Mutating internal state
      capacity = newCapacity
      popAtCapacity = capacity >> 2
      array = copy
    }
    result.asInstanceOf[A]
  }

  /** Returns a shallow copy of this stack. */
  override def clone(): ArrayStack[A] = {
    val copy = new Array[AnyRef](array.length)
    System.arraycopy(array, 0, copy, 0, index)
    new ArrayStack[A](copy, minCapacity, index)
  }
}
