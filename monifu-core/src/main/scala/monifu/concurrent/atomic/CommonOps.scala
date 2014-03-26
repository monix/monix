package monifu.concurrent.atomic

import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

private[atomic] trait CommonOps[@specialized T] { self: Atomic[T] =>
  import CommonOps._

  def weakCompareAndSet(expect: T, update: T): Boolean
  
  @tailrec
  @throws(classOf[InterruptedException])
  final def awaitCompareAndSet(expect: T, update: T): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      awaitCompareAndSet(expect, update)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def awaitCompareAndSet(expect: T, update: T, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    awaitCompareAndSet(expect, update, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def awaitCompareAndSet(expect: T, update: T, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      awaitCompareAndSet(expect, update, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  final def awaitValue(expect: T): Unit =
    if (get != expect) {
      interruptedCheck()
      awaitValue(expect)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def awaitValue(expect: T, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    awaitValue(expect, waitUntil)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def awaitValue(expect: T, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      awaitValue(expect, waitUntil)
    }

  @tailrec
  @throws(classOf[InterruptedException])
  final def awaitCondition(p: T => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      awaitCondition(p)
    }

  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def awaitCondition(waitAtMost: FiniteDuration)(p: T => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    awaitCondition(waitUntil)(p)
  }

  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def awaitCondition(waitUntil: Long)(p: T => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      awaitCondition(waitUntil)(p)
    }

  @tailrec
  final def transformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = get
    val (update, extract) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def weakTransformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = get
    val (update, extract) = cb(current)
    if (!weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def weakTransformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def weakGetAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  final def weakTransform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      weakTransform(cb)
  }
}

private[atomic] object CommonOps {
  def interruptedCheck(): Unit = {
    if (Thread.interrupted)
      throw new InterruptedException()
  }

  def timeoutCheck(endsAtNanos: Long): Unit = {
    if (System.nanoTime >= endsAtNanos)
      throw new TimeoutException()
  }
}