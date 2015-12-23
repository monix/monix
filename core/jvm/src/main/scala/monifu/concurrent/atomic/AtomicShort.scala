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
 
package monifu.concurrent.atomic

import scala.annotation.tailrec
import java.util.concurrent.atomic.{AtomicInteger => JavaAtomicInteger}

final class AtomicShort private (ref: JavaAtomicInteger)
  extends AtomicNumber[Short] {

  private[this] val mask = 255 + 255 * 256

  def get: Short =
    (ref.get() & mask).toShort

  def set(update: Short) = {
    ref.set(update)
  }

  def lazySet(update: Short) = {
    ref.lazySet(update)
  }

  def compareAndSet(expect: Short, update: Short): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Short): Short = {
    (ref.getAndSet(update) & mask).asInstanceOf[Short]
  }

  @tailrec
  def transformAndExtract[U](cb: (Short) => (U, Short)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Short) => Short): Short = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Short) => Short): Short = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Short) => Short): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Short): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Short = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Short): Short = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Short = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Short): Short = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Short): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Short): Short = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Short): Short = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Short = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Short = getAndIncrement(-v)

  private[this] def plusOp(a: Short, b: Short): Short = ((a + b) & mask).asInstanceOf[Short]
  private[this] def minusOp(a: Short, b: Short): Short = ((a - b) & mask).asInstanceOf[Short]
  private[this] def incrOp(a: Short, b: Int): Short = ((a + b) & mask).asInstanceOf[Short]
}

object AtomicShort {
  def apply(initialValue: Short): AtomicShort =
    new AtomicShort(new JavaAtomicInteger(initialValue))

  def wrap(ref: JavaAtomicInteger): AtomicShort =
    new AtomicShort(ref)
}
