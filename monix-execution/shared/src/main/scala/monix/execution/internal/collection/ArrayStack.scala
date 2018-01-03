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


/** Provides a fast platform-specific array-based stack. */
private[monix] abstract class ArrayStack[A]
  extends Serializable with Cloneable {

  /** Returns `true` if the stack is empty. */
  def isEmpty: Boolean

  /** Returns the size of our stack. */
  def size: Int

  /** Returns the current capacity of the internal array, which
    * grows and shrinks in response to `push` and `pop` operations.
    */
  def currentCapacity: Int

  /** Returns the minimum capacity of the internal array. */
  def minimumCapacity: Int

  /** Pushes an item in the stack. */
  def push(a: A): Unit

  /** Pops an item from the stack (in LIFO order).
    *
    * Returns `null` in case the stack is empty.
    */
  def pop(): A

  /** Returns a shallow copy of this stack. */
  override def clone(): ArrayStack[A] = {
    // $COVERAGE-OFF$
    super.clone().asInstanceOf[ArrayStack[A]]
    // $COVERAGE-ON$
  }
}

private[monix] object ArrayStack {
  /** Builds a platform-specific [[ArrayStack]] instance. */
  def apply[A](minCapacity: Int): ArrayStack[A] =
    new ArrayStackImpl[A](minCapacity)
}