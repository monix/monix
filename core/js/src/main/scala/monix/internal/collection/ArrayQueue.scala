/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

import scala.scalajs.js

/**
 * A Javascript-array based queue.
 *
 * Inspired by: http://code.stephenmorley.org/javascript/queues/
 */
private[monix] final class ArrayQueue[T] private
  (bufferSize: Int, triggerEx: Int => Throwable = null)
  extends EvictingQueue[T] {

  private[this] var queue = new js.Array[T]()
  private[this] var offset = 0

  val capacity = {
    if (bufferSize > 0)
      bufferSize
    else
      Int.MaxValue
  }

  def isAtCapacity: Boolean = {
    queue.length - offset >= capacity
  }

  def length: Int = {
    queue.length - offset
  }

  override def isEmpty: Boolean = {
    queue.length == 0
  }

  def offer(elem: T): Int = {
    if (elem == null) throw null
    if (bufferSize > 0 && queue.length - offset >= capacity) {
      if (triggerEx != null) throw triggerEx(capacity)
      1 // rejecting new element as we are at capacity
    }
    else {
      queue.push(elem)
      0
    }
  }

  def poll(): T = {
    if (queue.length == 0) null.asInstanceOf[T] else {
      val item = queue(offset)
      offset += 1

      if (offset * 2 >= queue.length) {
        queue = queue.jsSlice(offset)
        offset = 0
      }

      item
    }
  }

  def offerMany(seq: T*): Long = {
    val iterator = seq.iterator
    var acc = 0L
    while (iterator.hasNext)
      acc += offer(iterator.next())
    acc
  }


  def pollMany(array: Array[T], offset: Int): Int = {
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

  def clear(): Unit = {
    queue = new js.Array[T]()
    offset = 0
  }

  def iterator: Iterator[T] = {
    val clone = queue.jsSlice(0)
    clone.iterator
  }
}

private[monix] object ArrayQueue {
  def unbounded[T]: ArrayQueue[T] =
    new ArrayQueue[T](0)

  def bounded[T](bufferSize: Int, triggerEx: Int => Throwable = null): ArrayQueue[T] =
    new ArrayQueue[T](bufferSize, triggerEx)
}
