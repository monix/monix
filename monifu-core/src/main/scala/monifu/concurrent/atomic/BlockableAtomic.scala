package monifu.concurrent.atomic

import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

/**
 * Represents Atomic references that can block the thread (with spin-locking)
 * in wait of a successful condition (is not supported on top of Scala.js).
 *
 * Useful when using an Atomic reference as a locking mechanism.
 */
trait BlockableAtomic[@specialized T] { self: Atomic[T] =>
  import BlockableAtomic._

  /**
   * Waits until the `compareAndSet` operation succeeds, e.g...
   * 1. until the old value == expected and the operation succeeds, or
   * 2. until the current thread is interrupted
   */
  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForCompareAndSet(expect: T, update: T): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      waitForCompareAndSet(expect, update)
    }

  /**
   * Waits until the `compareAndSet` operation succeeds, e.g...
   * 1. until the old value == expected and the operation succeeds, or
   * 2. until the current thread is interrupted, or
   * 3. the specified timeout is due
   *
   * So this can throw an exception on timeout, useful for when you want to insure
   * that you don't block the current thread ad infinitum
   *
   * @param waitAtMost specifies the timeout, after which this method throws a TimeoutException
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def waitForCompareAndSet(expect: T, update: T, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCompareAndSet(expect, update, waitUntil)
  }

  /**
   * For private use only within `monifu`.
   */
  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def waitForCompareAndSet(expect: T, update: T, waitUntil: Long): Unit =
    if (!compareAndSet(expect, update)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCompareAndSet(expect, update, waitUntil)
    }

  /**
   * Waits until the specified `expect` value == the value stored by this Atomic reference
   * or until the current thread gets interrupted.
   */
  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForValue(expect: T): Unit =
    if (get != expect) {
      interruptedCheck()
      waitForValue(expect)
    }

  /**
   * Waits until the specified `expect` value == the value stored by this Atomic reference
   * or until the current thread gets interrupted.
   *
   * This can throw an exception on timeout, useful for when you want to insure
   * that you don't block the current thread ad infinitum
   *
   * @param waitAtMost specifies the timeout, after which this method throws a TimeoutException
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def waitForValue(expect: T, waitAtMost: FiniteDuration): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForValue(expect, waitUntil)
  }

  /**
   * For private use only within the `monifu` package.
   */
  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def waitForValue(expect: T, waitUntil: Long): Unit =
    if (get != expect) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForValue(expect, waitUntil)
    }

  /**
   * Waits until the specified callback, that receives the current value, returns `true`.
   */
  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForCondition(p: T => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      waitForCondition(p)
    }

  /**
   * Waits until the specified callback, that receives the current value, returns `true`.
   * Throws a `TimeoutException` in case the specified `waitAtMost` timeout is due.
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  final def waitForCondition(waitAtMost: FiniteDuration)(p: T => Boolean): Unit = {
    val waitUntil = System.nanoTime + waitAtMost.toNanos
    waitForCondition(waitUntil)(p)
  }

  /**
   * For private use only by the `monifu` package.
   */
  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] final def waitForCondition(waitUntil: Long)(p: T => Boolean): Unit =
    if (!p(get)) {
      interruptedCheck()
      timeoutCheck(waitUntil)
      waitForCondition(waitUntil)(p)
    }
}

object BlockableAtomic {
  /**
   * For private use only by the `monifu` package.
   *
   * Checks if the current thread has been interrupted, throwing
   * an `InterruptedException` in case it is.
   */
  private[atomic] def interruptedCheck(): Unit = {
    if (Thread.interrupted)
      throw new InterruptedException()
  }

  /**
   * For private use only by the `monifu` package.
   *
   * Checks if the timeout is due, throwing a `TimeoutException` in case it is.
   */
  private[atomic] def timeoutCheck(endsAtNanos: Long): Unit = {
    if (System.nanoTime >= endsAtNanos)
      throw new TimeoutException()
  }
}