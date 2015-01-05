/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
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

class AtomicChar private (ref: JavaAtomicInteger)
  extends AtomicNumber[Char] with BlockableAtomic[Char] {

  private[this] val mask = 255 + 255 * 256

  final def get: Char =
    (ref.get & mask).asInstanceOf[Char]

  final def set(update: Char) = {
    ref.set(update)
  }

  final def lazySet(update: Char) = {
    ref.lazySet(update)
  }

  final def compareAndSet(expect: Char, update: Char): Boolean = {
    ref.compareAndSet(expect, update)
  }

  final def getAndSet(update: Char): Char = {
    (ref.getAndSet(update) & mask).asInstanceOf[Char]
  }

  final def update(value: Char): Unit = set(value)

  final def `:=`(value: Char): Unit = set(value)

  @tailrec
  final def transformAndExtract[U](cb: (Char) => (U, Char)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (Char) => Char): Char = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (Char) => Char): Char = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (Char) => Char): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForCompareAndSet(expect: Char, update: Char): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForCompareAndSet(expect: Char, update: Char, maxRetries: Int): Boolean =
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
  final def waitForCompareAndSet(expect: Char, update: Char, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def waitForCompareAndSet(expect: Char, update: Char, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForValue(expect: Char): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def waitForValue(expect: Char, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def waitForValue(expect: Char, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForCondition(p: Char => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def waitForCondition(waitAtMost: FiniteDuration, p: Char => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def waitForCondition(waitUntil: Long, p: Char => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCondition(waitUntil, p)
    }

  @tailrec
  final def increment(v: Int = 1): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  final def add(v: Char): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  final def incrementAndGet(v: Int = 1): Char = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  final def addAndGet(v: Char): Char = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  final def getAndIncrement(v: Int = 1): Char = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  final def getAndAdd(v: Char): Char = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  final def subtract(v: Char): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  final def subtractAndGet(v: Char): Char = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  final def getAndSubtract(v: Char): Char = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @tailrec
  final def countDownToZero(v: Char = 1): Char = {
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

  final def decrement(v: Int = 1): Unit = increment(-v)
  final def decrementAndGet(v: Int = 1): Char = incrementAndGet(-v)
  final def getAndDecrement(v: Int = 1): Char = getAndIncrement(-v)
  final def `+=`(v: Char): Unit = addAndGet(v)
  final def `-=`(v: Char): Unit = subtractAndGet(v)

  private[this] final def plusOp(a: Char, b: Char): Char =
    ((a + b) & mask).asInstanceOf[Char]

  private[this] final def minusOp(a: Char, b: Char): Char =
    ((a - b) & mask).asInstanceOf[Char]

  private[this] final def incrOp(a: Char, b: Int): Char =
    ((a + b) & mask).asInstanceOf[Char]
}

object AtomicChar {
  def apply(initialValue: Char): AtomicChar =
    new AtomicChar(new JavaAtomicInteger(initialValue))

  def wrap(ref: JavaAtomicInteger): AtomicChar =
    new AtomicChar(ref)
}
