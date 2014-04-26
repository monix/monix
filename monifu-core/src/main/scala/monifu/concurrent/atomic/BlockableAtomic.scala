package monifu.concurrent.atomic

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

/**
 * Represents Atomic references that can block the thread (with spin-locking)
 * in wait of a successful condition (is not supported on top of Scala.js).
 *
 * Useful when using an Atomic reference as a locking mechanism.
 */
trait BlockableAtomic[T] extends Atomic[T] {
  /**
   * Waits until the `compareAndSet` operation succeeds, e.g...
   * 1. until the old value == expected and the operation succeeds, or
   * 2. until the current thread is interrupted
   */
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: T, update: T): Unit

  /**
   * Waits until the `compareAndSet` operation succeeds, e.g...
   *
   * 1. until the old value == expected and the operation succeeds, or
   * 2. until the current thread is interrupted
   * 3. until the the spin lock retried for a maximum of `maxRetries`
   *
   * @param expect the expected current value
   * @param update the value to replace the current value
   * @param maxRetries the maximum number of times to retry in case of failure
   *
   * @return true if the operation succeeded or false in case it failed after
   *         it retried for `maxRetries` times
   */
  @throws(classOf[InterruptedException])
  def waitForCompareAndSet(expect: T, update: T, maxRetries: Int): Boolean

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
  def waitForCompareAndSet(expect: T, update: T, waitAtMost: FiniteDuration): Unit

  /**
   * For private use only within `monifu`.
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCompareAndSet(expect: T, update: T, waitUntil: Long): Unit

  /**
   * Waits until the specified `expect` value == the value stored by this Atomic reference
   * or until the current thread gets interrupted.
   */
  @throws(classOf[InterruptedException])
  def waitForValue(expect: T): Unit

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
  def waitForValue(expect: T, waitAtMost: FiniteDuration): Unit

  /**
   * For private use only within the `monifu` package.
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForValue(expect: T, waitUntil: Long): Unit

  /**
   * Waits until the specified callback, that receives the current value, returns `true`.
   */
  @throws(classOf[InterruptedException])
  def waitForCondition(p: T => Boolean): Unit

  /**
   * Waits until the specified callback, that receives the current value, returns `true`.
   * Throws a `TimeoutException` in case the specified `waitAtMost` timeout is due.
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  def waitForCondition(waitAtMost: FiniteDuration, p: T => Boolean): Unit

  /**
   * For private use only by the `monifu` package.
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[monifu] def waitForCondition(waitUntil: Long, p: T => Boolean): Unit
}
