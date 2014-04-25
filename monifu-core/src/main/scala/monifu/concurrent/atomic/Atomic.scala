package monifu.concurrent.atomic

import scala.annotation.tailrec
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

/**
 * Base trait of all atomic references, no matter the type.
 */
trait Atomic[@specialized T] {
  import Atomic.{interruptedCheck, timeoutCheck}

  /**
   * @return the current value persisted by this Atomic
   */
  def get: T

  /**
   * @return the current value persisted by this Atomic, an alias for `get()`
   */
  @inline final def apply(): T = get

  /**
   * Updates the current value.
   * @param update will be the new value returned by `get()`
   */
  def set(update: T): Unit

  /**
   * Alias for `set()`. Updates the current value.
   * @param value will be the new value returned by `get()`
   */
  final def update(value: T): Unit = set(value)

  /**
   * Alias for `set()`. Updates the current value.
   * @param value will be the new value returned by `get()`
   */
  final def `:=`(value: T): Unit = set(value)

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
  @tailrec
  final def transformAndExtract[U](cb: (T) => (U, T)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

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
  @tailrec
  final def transformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

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
  @tailrec
  final def getAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

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
  @tailrec
  final def transform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

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
  @tailrec
  @throws(classOf[InterruptedException])
  final def waitForCompareAndSet(expect: T, update: T, maxRetries: Int): Boolean =
    if (!compareAndSet(expect, update))
      if (maxRetries > 0) {
        interruptedCheck()
        waitForCompareAndSet(expect, update, maxRetries - 1)
      }
      else
        false
    else
      true

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

  /**
   * Atomically sets the value to the given updated value if the current value == the expected value.
   *
   * May fail spuriously and does not provide ordering guarantees, so is only rarely an appropriate
   * alternative to compareAndSet.
   *
   * @param expect is the previous value
   * @param update is the new value
   * @return true if the operation succeeded or false otherwise
   */
  def weakCompareAndSet(expect: T, update: T): Boolean

  /**
   * Eventually sets to the given value. Has weaker visibility guarantees than the normal `set()`
   * and is thus less useful.
   */
  def lazySet(update: T): Unit

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
  final def weakTransformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
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
  final def weakTransform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      weakTransform(cb)
  }
}

object Atomic {
  /**
   * Constructs an `Atomic[T]` reference. Based on the `initialValue`, it will return the best, most specific
   * type. E.g. you give it a number, it will return something inheriting from `AtomicNumber[T]`. That's why
   * it takes an `AtomicBuilder[T, R]` as an implicit parameter - but worry not about such details as it just works.
   *
   * @param initialValue is the initial value with which to initialize the Atomic reference
   * @param builder is the builder that helps us to build the best reference possible, based on our `initialValue`
   */
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)

  /**
   * Returns the builder that would be chosen to construct Atomic references
   * for the given `initialValue`.
   */
  def builderFor[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): AtomicBuilder[T, R] =
    builder

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