package monifu.concurrent.atomic2

import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

/**
 * Base trait of all atomic references, no matter the type.
 */
trait Atomic[T] extends Any {
  /**
   * @return the current value persisted by this Atomic
   */
  def get: T

  /**
   * @return the current value persisted by this Atomic, an alias for `get()`
   */
  def apply(): T = get

  /**
   * Updates the current value.
   * @param update will be the new value returned by `get()`
   */
  def set(update: T): Unit

  /**
   * Alias for `set()`. Updates the current value.
   * @param value will be the new value returned by `get()`
   */
  def update(value: T): Unit

  /**
   * Alias for `set()`. Updates the current value.
   * @param value will be the new value returned by `get()`
   */
  def `:=`(value: T): Unit

  /**
   * Does a compare-and-set operation on the current value. For more info, checkout the related
   * [[https://en.wikipedia.org/wiki/Compare-and-swap Compare-and-swap Wikipedia page]].
   *
   * It's an atomic, worry free operation.
   *
   * @param expect is the value you expect to be persisted when the operation happens
   * @param update will be the new value, should the check for `expect` succeeds
   * @return either true in case the operation succeeded or false otherwise
   */
  def compareAndSet(expect: T, update: T): Boolean

  /**
   * Sets the persisted value to `update` and returns the old value that was in place.
   * It's an atomic, worry free operation.
   */
  def getAndSet(update: T): T

  /**
   * Eventually sets to the given value. Has weaker visibility guarantees than the normal `set()`
   * and is thus less useful.
   */
  def lazySet(update: T): Unit

  /**
   * Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
   * executed, a callback that transforms the current value. This method will loop until it will
   * succeed in replacing the current value with the one produced by your callback.
   *
   * Note that the callback will be executed on each iteration of the loop, so it can be called
   * multiple times - don't do destructive I/O or operations that mutate global state in it.
   *
   * @param cb is a callback that receives the current value as input and returns a tuple that specifies
   *           the update + what should this method return when the operation succeeds.
   * @return whatever was specified by your callback, once the operation succeeds
   */
  def transformAndExtract[U](cb: (T) => (T, U)): U

  /**
   * Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
   * executed, a callback that transforms the current value. This method will loop until it will
   * succeed in replacing the current value with the one produced by the given callback.
   *
   * Note that the callback will be executed on each iteration of the loop, so it can be called
   * multiple times - don't do destructive I/O or operations that mutate global state in it.
   *
   * @param cb is a callback that receives the current value as input and returns the `update` which is the
   *           new value that should be persisted
   * @return whatever the update is, after the operation succeeds
   */
  def transformAndGet(cb: (T) => T): T

  /**
   * Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
   * executed, a callback that transforms the current value. This method will loop until it will
   * succeed in replacing the current value with the one produced by the given callback.
   *
   * Note that the callback will be executed on each iteration of the loop, so it can be called
   * multiple times - don't do destructive I/O or operations that mutate global state in it.
   *
   * @param cb is a callback that receives the current value as input and returns the `update` which is the
   *           new value that should be persisted
   * @return the old value, just prior to when the successful update happened
   */
  def getAndTransform(cb: (T) => T): T

  /**
   * Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
   * executed, a callback that transforms the current value. This method will loop until it will
   * succeed in replacing the current value with the one produced by the given callback.
   *
   * Note that the callback will be executed on each iteration of the loop, so it can be called
   * multiple times - don't do destructive I/O or operations that mutate global state in it.
   *
   * @param cb is a callback that receives the current value as input and returns the `update` which is the
   *           new value that should be persisted
   */
  def transform(cb: (T) => T): Unit

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

