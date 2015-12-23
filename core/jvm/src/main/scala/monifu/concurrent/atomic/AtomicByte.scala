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

import java.util.concurrent.atomic.{AtomicInteger => JavaAtomicInteger}
import scala.annotation.tailrec

final class AtomicByte private (ref: JavaAtomicInteger) extends AtomicNumber[Byte] {
  private[this] val mask = 255

  def get: Byte =
    (ref.get & mask).toByte

  def set(update: Byte) = {
    ref.set(update)
  }

  def lazySet(update: Byte) = {
    ref.lazySet(update)
  }

  def compareAndSet(expect: Byte, update: Byte): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Byte): Byte = {
    (ref.getAndSet(update) & mask).toByte
  }

  @tailrec
  def transformAndExtract[U](cb: (Byte) => (U, Byte)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Byte) => Byte): Byte = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Byte) => Byte): Byte = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Byte) => Byte): Unit = {
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
  def add(v: Byte): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Byte = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Byte): Byte = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Byte = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Byte): Byte = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Byte): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Byte): Byte = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Byte): Byte = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Byte = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Byte = getAndIncrement(-v)

  private[this] def plusOp(a: Byte, b: Byte): Byte =
    ((a + b) & mask).asInstanceOf[Byte]

  private[this] def minusOp(a: Byte, b: Byte): Byte =
    ((a - b) & mask).asInstanceOf[Byte]

  private[this] def incrOp(a: Byte, b: Int): Byte =
    ((a + b) & mask).asInstanceOf[Byte]
}

object AtomicByte {
  def apply(initialValue: Byte): AtomicByte =
    new AtomicByte(new JavaAtomicInteger(initialValue))

  def wrap(ref: JavaAtomicInteger): AtomicByte =
    new AtomicByte(ref)
}
