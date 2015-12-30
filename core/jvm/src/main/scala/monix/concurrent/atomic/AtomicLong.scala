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

package monix.concurrent.atomic

import java.util.concurrent.atomic.{AtomicLong => JavaAtomicLong}
import scala.annotation.tailrec

final class AtomicLong private (ref: JavaAtomicLong) 
  extends AtomicNumber[Long] {
  
  def get: Long = ref.get()

  def set(update: Long): Unit = {
    ref.set(update)
  }

  def compareAndSet(expect: Long, update: Long): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Long): Long = {
    ref.getAndSet(update)
  }

  def lazySet(update: Long): Unit = {
    ref.lazySet(update)
  }

  @tailrec
  def transformAndExtract[U](cb: (Long) => (U, Long)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Long) => Long): Long = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Long) => Long): Long = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Long) => Long): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = ref.get()
    if (!compareAndSet(current, current+v))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Long = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Long = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Long): Long = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: Long): Long = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: Long): Unit = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      add(v)
  }

  def subtract(v: Long): Unit =
    add(-v)

  def getAndSubtract(v: Long): Long =
    getAndAdd(-v)

  def subtractAndGet(v: Long): Long =
    addAndGet(-v)

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Long = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Long = getAndIncrement(-v)

  override def toString: String =
    s"AtomicLong(${ref.get()})"
}

object AtomicLong {
  def apply(initialValue: Long): AtomicLong =
    new AtomicLong(new JavaAtomicLong(initialValue))

  def wrap(ref: JavaAtomicLong): AtomicLong =
    new AtomicLong(ref)
}
