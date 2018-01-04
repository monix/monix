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

/** Light and fast array-based stack implementation,
  * for internal usage.
  */
private[collection] final class ArrayStackImpl[A] private (
  private[this] val minCapacity: Int,
  private[this] var array: js.Array[AnyRef],
  private[this] var index: Int)
  extends ArrayStack[A] {

  private[this] var capacity = array.length
  private[this] val popCapacityThreshold = minCapacity << 1 // * 2

  def this(minCapacity: Int) =
    this(minCapacity, new js.Array[AnyRef](math.nextPowerOf2(minCapacity)), 0)

  override def clone(): ArrayStack[A] = {
    val copy = array.jsSlice(0, array.length)
    new ArrayStackImpl[A](minCapacity, copy, index)
  }

  def size: Int = index
  def currentCapacity: Int = capacity
  def minimumCapacity: Int = minCapacity
  def isEmpty: Boolean = index == 0

  def push(a: A): Unit = {
    // If over capacity, we must double the array size!
    if (index >= capacity) {
      capacity = capacity * 2
      array.length = capacity
    }

    array(index) = a.asInstanceOf[AnyRef]
    index += 1
  }

  def pop(): A = {
    if (index == 0) return null.asInstanceOf[A]
    index -= 1
    val result = array(index)

    // Shrinks array, but it's conservative
    if (capacity >= popCapacityThreshold && index <= (capacity >> 2)) {
      capacity = capacity >> 1
      array.length = capacity
    }

    result.asInstanceOf[A]
  }
}
