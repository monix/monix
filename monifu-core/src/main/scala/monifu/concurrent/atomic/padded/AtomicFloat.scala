package monifu.concurrent.atomic.padded

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import java.lang.Float.{intBitsToFloat, floatToIntBits}
import monifu.concurrent.atomic.{AtomicNumber, BlockableAtomic, interruptedCheck, timeoutCheck}
import monifu.concurrent.misc.Unsafe

final class AtomicFloat private (initialValue: Float)
  extends Atomic[Float] with AtomicNumber[Float] with BlockableAtomic[Float] {

  private[this] val offset = AtomicFloat.addressOffset

  @volatile private[this] var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16 = 10L
  @volatile private[this] var value: Int = floatToIntBits(initialValue)
  @volatile private[this] var s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16 = 10L

  @inline def get: Float =
    intBitsToFloat(value)

  @inline def set(update: Float) = {
    value = floatToIntBits(update)
  }

  @inline def lazySet(update: Float) = {
    Unsafe.putOrderedInt(this, offset, floatToIntBits(update))
  }

  @inline def compareAndSet(expect: Float, update: Float): Boolean = {
    val current = value
    current == floatToIntBits(expect) && Unsafe.compareAndSwapInt(this, offset, current, floatToIntBits(update))
  }

  @tailrec
  def getAndSet(update: Float): Float = {
    val current = value
    if (Unsafe.compareAndSwapInt(this, offset, current, floatToIntBits(update)))
      intBitsToFloat(current)
    else
      getAndSet(update)
  }

  @inline def update(value: Float): Unit = set(value)
  @inline def `:=`(value: Float): Unit = set(value)

  @tailrec
  def transformAndExtract[U](cb: (Float) => (U, Float)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Float) => Float): Float = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Float) => Float): Float = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Float) => Float): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Float, update: Float): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Float, update: Float, maxRetries: Int): Boolean =
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
  def waitForCompareAndSet(expect: Float, update: Float, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: Float, update: Float, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForValue(expect: Float): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForValue(expect: Float, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: Float, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCondition(p: Float => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: Float => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: Float => Boolean): Unit =
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
  def add(v: Float): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Float = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Float): Float = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Float = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Float): Float = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Float): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Float): Float = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Float): Float = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @tailrec
  def countDownToZero(v: Float = 1.0f): Float = {
    val current = get
    if (current != 0.0f) {
      val decrement = if (current >= v) v else current
      val update = current - decrement
      if (!compareAndSet(current, update))
        countDownToZero(v)
      else
        decrement
    }
    else
      0.0f
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Float = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Float = getAndIncrement(-v)
  def `+=`(v: Float): Unit = addAndGet(v)
  def `-=`(v: Float): Unit = subtractAndGet(v)

  @inline private[this] def plusOp(a: Float, b: Float): Float = a + b
  @inline private[this] def minusOp(a: Float, b: Float): Float = a - b
  @inline private[this] def incrOp(a: Float, b: Int): Float = a + b
}

object AtomicFloat {
  def apply(initialValue: Float): AtomicFloat =
    new AtomicFloat(initialValue)

  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[AtomicFloat].getFields.find(_.getName.endsWith("value")).get)
}
