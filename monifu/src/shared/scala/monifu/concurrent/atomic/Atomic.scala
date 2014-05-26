package monifu.concurrent.atomic

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
   * Eventually sets to the given value. Has weaker visibility guarantees than the normal `set()`.
   *
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
  def transformAndExtract[U](cb: (T) => (U, T)): U

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
}