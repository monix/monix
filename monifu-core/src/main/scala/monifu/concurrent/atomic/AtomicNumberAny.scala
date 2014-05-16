package monifu.concurrent.atomic

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.misc.Unsafe


final class AtomicNumberAny[T : Numeric] private (initialValue: T) extends AtomicNumber[T] with BlockableAtomic[T] {
  @volatile private[this] var value = initialValue
  private[this] val offset = AtomicNumberAny.addressOffset
  private[this] val ev = implicitly[Numeric[T]]

  @inline def get: T = value

  @inline def set(update: T): Unit = {
    value = update
  }

  def update(value: T): Unit = set(value)
  def `:=`(value: T): Unit = set(value)

  @inline def compareAndSet(expect: T, update: T): Boolean = {
    val current = value
    current == expect && Unsafe.compareAndSwapObject(this, offset, current.asInstanceOf[AnyRef], update.asInstanceOf[AnyRef])
  }

  @tailrec
  def getAndSet(update: T): T = {
    val current = value
    if (Unsafe.compareAndSwapObject(this, offset, current.asInstanceOf[AnyRef], update.asInstanceOf[AnyRef]))
      current
    else
      getAndSet(update)
  }

  @inline def lazySet(update: T): Unit = {
    Unsafe.putOrderedObject(this, offset, update.asInstanceOf[AnyRef])
  }

  @tailrec
  def transformAndExtract[U](cb: (T) => (U, T)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: T, update: T): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: T, update: T, maxRetries: Int): Boolean =
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
  def waitForCompareAndSet(expect: T, update: T, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: T, update: T, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForValue(expect: T): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForValue(expect: T, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: T, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCondition(p: T => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: T => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: T => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCondition(waitUntil, p)
    }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = value
    if (!compareAndSet(current, ev.plus(current, ev.fromInt(v))))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): T = {
    val current = value
    val update = ev.plus(current, ev.fromInt(v))
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): T = {
    val current = value
    val update = ev.plus(current, ev.fromInt(v))
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: T): T = {
    val current = value
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: T): T = {
    val current = value
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: T): Unit = {
    val current = value
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def subtract(v: T): Unit = {
    val current = value
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def getAndSubtract(v: T): T = {
    val current = value
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @tailrec
  def subtractAndGet(v: T): T = {
    val current = value
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def countDownToZero(v: T = ev.one): T = {
    val current = get
    if (current != ev.zero) {
      val decrement = if (ev.compare(current, v) >= 0) v else current
      val update = ev.minus(current, decrement)
      if (!compareAndSet(current, update))
        countDownToZero(v)
      else
        decrement
    }
    else
      ev.zero
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): T = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): T = getAndIncrement(-v)
  def `+=`(v: T): Unit = addAndGet(v)
  def `-=`(v: T): Unit = subtractAndGet(v)
}

object AtomicNumberAny {
  def apply[T : Numeric](initialValue: T): AtomicNumberAny[T] =
    new AtomicNumberAny(initialValue)

  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[AtomicNumberAny[_]].getFields.find(_.getName.endsWith("value")).get)
}
