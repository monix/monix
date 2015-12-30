/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
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

package monix.internal.collection

private[monix] trait EvictingQueue[T] extends Buffer[T] {
  /**
   * Returns the capacity of this queue.
   */
  def capacity: Int

  /**
   * Returns true if the queue is at capacity.
   */
  def isAtCapacity: Boolean

  /**
   * Pushes a new element in the queue. On overflow, it starts
   * to evict old elements from the queue.
   *
   * @return the number of elements that were evicted in case of
   *         overflow or zero otherwise
   */
  def offer(elem: T): Int

  /**
   * Pushes the given sequence of elements on the queue. On overflow,
   * it starts to evict old elements from the queue.
   */
  def offerMany(seq: T*): Long

  /**
   * Returns the first element in the queue, and removes this element
   * from the queue.
   *
   * @return the first element of the queue or `null` if empty
   */
  def poll(): T

  /**
   * Given an array, polls as many available elements in it as
   * can fit and returns the number of elements that were copied,
   * a number that will be equal to `min(queue.length, array.length)`.
   */
  def pollMany(array: Array[T], offset: Int = 0): Int
}