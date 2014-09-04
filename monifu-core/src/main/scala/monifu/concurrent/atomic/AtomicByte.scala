/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.atomic.{AtomicInteger => JavaAtomicInteger}

final class AtomicByte private (ref: JavaAtomicInteger)
  extends AtomicNumber[Byte] with BlockableAtomic[Byte] {
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

  def update(value: Byte): Unit = set(value)

  def `:=`(value: Byte): Unit = set(value)

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
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Byte, update: Byte): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Byte, update: Byte, maxRetries: Int): Boolean =
    if (!compareAndSet(expect, update))
      if (maxRetries > 0) {
        interruptedCheck()
        waitForCompareAndSet(expect, update, maxRetries - 1)
      }
      else
        false
    else
      true

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCompareAndSet(expect: Byte, update: Byte, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: Byte, update: Byte, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForValue(expect: Byte): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForValue(expect: Byte, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: Byte, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCondition(p: Byte => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: Byte => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: Byte => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCondition(waitUntil, p)
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

  @tailrec
  def countDownToZero(v: Byte = 1): Byte = {
    val current = get
    if (current != 0) {
      val decrement = if (current >= v) v else current
      val update = minusOp(current, decrement)
      if (!compareAndSet(current, update))
        countDownToZero(v)
      else
        decrement
    }
    else
      0
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Byte = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Byte = getAndIncrement(-v)
  def `+=`(v: Byte): Unit = addAndGet(v)
  def `-=`(v: Byte): Unit = subtractAndGet(v)

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
