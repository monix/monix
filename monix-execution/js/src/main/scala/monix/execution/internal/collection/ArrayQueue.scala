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

import monix.execution.internal.math._

import scala.collection.mutable
import scala.scalajs.js

/** A Javascript Array based queue.
  *
  * Inspired by: http://code.stephenmorley.org/javascript/queues/
  */
private[monix] final class ArrayQueue[A] private
  (_size: Int, triggerEx: Int => Throwable = null)
  extends EvictingQueue[A] {

  private[this] var queue = new js.Array[A]()
  private[this] var offset = 0
  private[this] val bufferSize =
    if (_size <= 0) 0 else nextPowerOf2(_size)

  override def capacity: Int =
    if (bufferSize == 0) Int.MaxValue else bufferSize
  override def isAtCapacity: Boolean =
    queue.length - offset >= capacity
  override def length: Int =
    queue.length - offset
  override def isEmpty: Boolean =
    queue.length - offset == 0
  override def nonEmpty: Boolean =
    queue.length - offset > 0

  def offer(elem: A): Int = {
    if (elem == null) throw new NullPointerException("Null not supported")
    if (bufferSize > 0 && queue.length - offset >= capacity) {
      if (triggerEx != null) throw triggerEx(capacity)
      1 // rejecting new element as we are at capacity
    }
    else {
      queue.push(elem)
      0
    }
  }

  def poll(): A = {
    if (queue.length == 0) null.asInstanceOf[A] else {
      val item = queue(offset)
      offset += 1

      if (offset * 2 >= queue.length) {
        queue = queue.jsSlice(offset)
        offset = 0
      }

      item
    }
  }

  def offerMany(seq: A*): Long = {
    val iterator = seq.iterator
    var acc = 0L
    while (iterator.hasNext)
      acc += offer(iterator.next())
    acc
  }

  def drainToArray(array: Array[A], offset: Int): Int = {
    var idx = offset
    var continue = true

    while (continue && idx < array.length) {
      val elem = poll()
      if (elem != null) {
        array(idx) = elem
        idx += 1
      }
      else {
        continue = false
      }
    }

    idx - offset
  }

  override def drainToBuffer(buffer: mutable.Buffer[A], limit: Int): Int = {
    var count = 0
    var continue = true

    while (continue && count < limit) {
      val elem = poll()
      if (elem != null) {
        buffer += elem
        count += 1
      }
      else {
        continue = false
      }
    }

    count
  }

  def clear(): Unit = {
    queue = new js.Array[A]()
    offset = 0
  }

  def iterator: Iterator[A] = {
    val clone = queue.jsSlice(0)
    clone.iterator
  }
}

private[monix] object ArrayQueue {
  def unbounded[A]: ArrayQueue[A] =
    new ArrayQueue[A](0)

  def bounded[A](bufferSize: Int, triggerEx: Int => Throwable = null): ArrayQueue[A] =
    new ArrayQueue[A](bufferSize, triggerEx)
}
