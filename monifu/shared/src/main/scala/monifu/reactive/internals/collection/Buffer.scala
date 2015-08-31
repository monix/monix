/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.collection

/**
 * A `Buffer` is a data-structure that can be appended in constant time
 * constant time and that can be iterated efficiently.
 */
private[reactive] trait Buffer[T] extends Iterable[T] {
  /**
   * Pushes a new element in the queue. Depending on
   * implementation, on overflow it might start to evict
   * old elements from the queue.
   *
   * @return the number of elements that were evicted in case of
   *         overflow or zero otherwise
   */
  def offer(elem: T): Int

  /**
   * Pushes the given sequence on the queue. Depending on
   * implementation, on overflow it might start to evict
   * old elements from the queue.
   *
   * @return the number of elements that were evicted in case of
   *         overflow or zero otherwise
   */
  def offerMany(seq: T*): Long

  /**
   * Clears all items in this buffer leaving it empty.
   */
  def clear(): Unit

  /** Returns the number of elements stored */
  def length: Int

  /** Returns the number of elements stored */
  def size: Int
}