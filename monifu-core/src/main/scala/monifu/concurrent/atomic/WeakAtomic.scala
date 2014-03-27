package monifu.concurrent.atomic

import scala.annotation.tailrec

/**
 * Interface for the weak operations that are supported on top of the JVM:
 * 1. `weakCompareAndSet`
 * 2. `lazySet`
 *
 * Also provides weak variants for the helpers in `Atomic[T]`
 */
trait WeakAtomic[@specialized T] { self: Atomic[T] =>
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
