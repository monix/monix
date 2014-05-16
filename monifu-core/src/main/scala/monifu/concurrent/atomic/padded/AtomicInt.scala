package monifu.concurrent.atomic.padded

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.atomic.{AtomicNumber, BlockableAtomic, interruptedCheck, timeoutCheck}
import monifu.concurrent.misc.Unsafe

final class AtomicInt private (initialValue: Int) extends Atomic[Int] with AtomicNumber[Int] with BlockableAtomic[Int] {
  @volatile private[this] var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16 = 10L
  @volatile private[this] var value = initialValue
  @volatile private[this] var s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16 = 10L

  private[this] val offset = AtomicInt.addressOffset

  @inline def get: Int = value

  @inline def set(update: Int): Unit = {
    value = update
  }

  def update(value: Int): Unit = set(value)
  def `:=`(value: Int): Unit = set(value)

  @inline def compareAndSet(expect: Int, update: Int): Boolean = {
    val current = value
    current == expect && Unsafe.compareAndSwapInt(this, offset, current, update)
  }

  @tailrec
  def getAndSet(update: Int): Int = {
    val current = value
    if (Unsafe.compareAndSwapInt(this, offset, current, update))
      current
    else
      getAndSet(update)
  }

  @inline def lazySet(update: Int): Unit = {
    Unsafe.putOrderedInt(this, offset, update)
  }

  @tailrec
  def transformAndExtract[U](cb: (Int) => (U, Int)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Int) => Int): Int = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Int) => Int): Int = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Int) => Int): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Int, update: Int): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Int, update: Int, maxRetries: Int): Boolean =
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
  def waitForCompareAndSet(expect: Int, update: Int, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: Int, update: Int, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForValue(expect: Int): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForValue(expect: Int, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: Int, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCondition(p: Int => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: Int => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: Int => Boolean): Unit =
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
  def incrementAndGet(v: Int = 1): Int = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Int = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Int): Int = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: Int): Int = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: Int): Unit = {
    val current = value
    val update = current + v
    if (!compareAndSet(current, update))
      add(v)
  }

  def subtract(v: Int): Unit =
    add(-v)

  def getAndSubtract(v: Int): Int =
    getAndAdd(-v)

  def subtractAndGet(v: Int): Int =
    addAndGet(-v)

  @tailrec
  def countDownToZero(v: Int = 1): Int = {
    val current = get
    if (current != 0) {
      val decrement = if (current >= v) v else current
      val update = current - decrement
      if (!compareAndSet(current, update))
        countDownToZero(v)
      else
        decrement
    }
    else
      0
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Int = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Int = getAndIncrement(-v)
  def `+=`(v: Int): Unit = addAndGet(v)
  def `-=`(v: Int): Unit = subtractAndGet(v)

  override def toString: String = s"AtomicInt($value)"
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt =
    new AtomicInt(initialValue)

  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[AtomicInt].getFields.find(_.getName.endsWith("value")).get)
}
