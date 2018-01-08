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

private[monix] trait EvictingQueue[A] extends Buffer[A] {
  /**
    * Returns the capacity of this queue.
    */
  def capacity: Int

  /**
    * Returns true if the queue is at capacity.
    */
  def isAtCapacity: Boolean

  /** Pushes a new element in the queue. On overflow, it starts
    * to evict old elements from the queue.
    *
    * @return the number of elements that were evicted in case of
    *         overflow or zero otherwise
    */
  def offer(elem: A): Int

  /** Pushes the given sequence of elements on the queue. On overflow,
    * it starts to evict old elements from the queue.
    */
  def offerMany(seq: A*): Long

  /** Returns the first element in the queue, and removes this element
    * from the queue.
    *
    * @return the first element of the queue or `null` if empty
    */
  def poll(): A

  /** Given an array, polls as many available elements in it as
    * can fit and returns the number of elements that were copied,
    * a number that will be equal to `min(queue.length, array.length)`.
    */
  def drainToArray(array: Array[A], offset: Int = 0): Int

  /** Given a Scala `Buffer`, polls as many available elements in the
    * queue, up to the maximum specified by `limit`.
    *
    * @param buffer is the buffer where to polled items
    * @param limit is the maximum limit applied to the number of polled items
    */
  def drainToBuffer(buffer: scala.collection.mutable.Buffer[A], limit: Int): Int
}