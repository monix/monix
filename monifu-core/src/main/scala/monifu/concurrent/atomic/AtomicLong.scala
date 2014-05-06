package monifu.concurrent.atomic

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.misc.Unsafe


final class AtomicLong private (initialValue: Long) extends AtomicNumber[Long] with BlockableAtomic[Long] {
  @volatile private[this] var value = initialValue
  private[this] val offset = AtomicLong.addressOffset

  @inline def get: Long = value

  @inline def set(update: Long): Unit = {
    value = update
  }

  def update(value: Long): Unit = set(value)
  def `:=`(value: Long): Unit = set(value)

  @inline def compareAndSet(expect: Long, update: Long): Boolean = {
    val current = value
    current == expect && Unsafe.compareAndSwapLong(this, offset, current, update)
  }

  @tailrec
  def getAndSet(update: Long): Long = {
    val current = value
    if (Unsafe.compareAndSwapLong(this, offset, current, update))
      current
    else
      getAndSet(update)
  }

  @inline def lazySet(update: Long): Unit = {
    Unsafe.putOrderedLong(this, offset, update)
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
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Long, update: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Long, update: Long, maxRetries: Int): Boolean =
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
  def waitForCompareAndSet(expect: Long, update: Long, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: Long, update: Long, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForValue(expect: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForValue(expect: Long, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: Long, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCondition(p: Long => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: Long => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: Long => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCondition(waitUntil, p)
    }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = value
    if (!compareAndSet(current, current+v))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Long = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Long = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Long): Long = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: Long): Long = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: Long): Unit = {
    val current = value
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
  def `+=`(v: Long): Unit = addAndGet(v)
  def `-=`(v: Long): Unit = subtractAndGet(v)

  override def toString: String = s"AtomicLong($value)"
}

object AtomicLong {
  def apply(initialValue: Long): AtomicLong =
    new AtomicLong(initialValue)

  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[AtomicLong].getFields.find(_.getName.endsWith("value")).get)
}
