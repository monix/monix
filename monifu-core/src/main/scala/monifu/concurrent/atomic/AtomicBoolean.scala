package monifu.concurrent.atomic

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.misc.Unsafe

final class AtomicBoolean private (initialValue: Boolean) extends BlockableAtomic[Boolean] {
  @volatile private[this] var value: Int = if (initialValue) 1 else 0
  private[this] val offset = AtomicBoolean.addressOffset

  @inline def get: Boolean = value == 1

  @inline def set(update: Boolean): Unit = {
    value = if (update) 1 else 0
  }

  def update(value: Boolean): Unit = set(value)
  def `:=`(value: Boolean): Unit = set(value)

  @inline def compareAndSet(expect: Boolean, update: Boolean): Boolean = {
    Unsafe.compareAndSwapInt(this, offset, if (expect) 1 else 0, if (update) 1 else 0)
  }

  @tailrec
  def getAndSet(update: Boolean): Boolean = {
    val current = get
    if (compareAndSet(get, update))
      current
    else
      getAndSet(update)
  }

  @inline def lazySet(update: Boolean): Unit = {
    Unsafe.putOrderedInt(this, offset, if (update) 1 else 0)
  }

  @tailrec
  def transformAndExtract[U](cb: (Boolean) => (U, Boolean)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Boolean) => Boolean): Boolean = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Boolean) => Boolean): Boolean = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Boolean) => Boolean): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Boolean, update: Boolean): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: Boolean, update: Boolean, maxRetries: Int): Boolean =
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
  def waitForCompareAndSet(expect: Boolean, update: Boolean, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: Boolean, update: Boolean, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForValue(expect: Boolean): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForValue(expect: Boolean, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: Boolean, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  def waitForCondition(p: Boolean => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: Boolean => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil, p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: Boolean => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCondition(waitUntil, p)
    }

  override def toString: String = s"AtomicBoolean(${value == 1})"
}

object AtomicBoolean {
  def apply(initialValue: Boolean): AtomicBoolean =
    new AtomicBoolean(initialValue)

  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[AtomicBoolean].getFields.find(_.getName.endsWith("value")).get)
}
