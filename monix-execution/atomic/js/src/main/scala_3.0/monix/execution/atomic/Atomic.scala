/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.execution.atomic

/**
  * Base trait of all atomic references, no matter the type.
  */
abstract class Atomic[A] extends Serializable {
  /** Get the current value persisted by this Atomic. */
  def get(): A

  /** Get the current value persisted by this Atomic, an alias for `get()`. */
  inline final def apply(): A = get()

  /** Updates the current value.
    *
    * @param update will be the new value returned by `get()`
    */
  def set(update: A): Unit

  /** Alias for [[set]]. Updates the current value.
    *
    * @param value will be the new value returned by `get()`
    */
  inline final def update(value: A): Unit = set(value)

  /** Alias for [[set]]. Updates the current value.
    *
    * @param value will be the new value returned by `get()`
    */
  inline final def `:=`(value: A): Unit = set(value)

  /** Does a compare-and-set operation on the current value. For more info, checkout the related
    * [[https://en.wikipedia.org/wiki/Compare-and-swap Compare-and-swap Wikipedia page]].
    *
    * It's an atomic, worry free operation.
    *
    * @param expect is the value you expect to be persisted when the operation happens
    * @param update will be the new value, should the check for `expect` succeeds
    * @return either true in case the operation succeeded or false otherwise
    */
  def compareAndSet(expect: A, update: A): Boolean

  /** Sets the persisted value to `update` and returns the old value that was in place.
    * It's an atomic, worry free operation.
    */
  def getAndSet(update: A): A

  /** Eventually sets to the given value.
    * Has weaker visibility guarantees than the normal `set()`.
    */
  def lazySet(update: A): Unit = set(update)

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
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
  inline final def transformAndExtract[U](inline cb: (A) => (U, A)): U = {
    val current = get()
    val (result, update) = cb(current)
    set(update)
    result
  }

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
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
  inline final def transformAndGet(inline cb: (A) => A): A = {
    val current = get()
    val update = cb(current)

    set(update)
    update
  }

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
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
  inline final def getAndTransform(inline cb: (A) => A): A = {
    val current = get()
    val update = cb(current)

    set(update)
    current
  }

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
    * executed, a callback that transforms the current value. This method will loop until it will
    * succeed in replacing the current value with the one produced by the given callback.
    *
    * Note that the callback will be executed on each iteration of the loop, so it can be called
    * multiple times - don't do destructive I/O or operations that mutate global state in it.
    *
    * @param cb is a callback that receives the current value as input and returns the `update` which is the
    *           new value that should be persisted
    */
  inline final def transform(inline cb: (A) => A): Unit =
    set(cb(get()))
}

object Atomic {
  /** Constructs an `Atomic[A]` reference.
    *
    * Based on the `initialValue`, it will return the best, most
    * specific type. E.g. you give it a number, it will return
    * something inheriting from `AtomicNumber[A]`. That's why it takes
    * an `AtomicBuilder[T, R]` as an implicit parameter - but worry
    * not about such details as it just works.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    *
    * @param builder is the builder that helps us to build the
    *        best reference possible, based on our `initialValue`
    */
  inline def apply[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): R =
    builder.buildInstance(initialValue, PaddingStrategy.NoPadding, allowPlatformIntrinsics = true)

  /** Constructs an `Atomic[A]` reference, applying the provided
    * [[PaddingStrategy]] in order to counter the "false sharing"
    * problem.
    *
    * Based on the `initialValue`, it will return the best, most
    * specific type. E.g. you give it a number, it will return
    * something inheriting from `AtomicNumber[A]`. That's why it takes
    * an `AtomicBuilder[A, R]` as an implicit parameter - but worry
    * not about such details as it just works.
    *
    * Note that for ''Scala.js'' we aren't applying any padding, as it
    * doesn't make much sense, since Javascript execution is single
    * threaded, but this builder is provided for syntax compatibility
    * anyway across the JVM and Javascript and we never know how
    * Javascript engines will evolve.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    *
    * @param padding is the [[PaddingStrategy]] to apply
    *
    * @param builder is the builder that helps us to build the
    *        best reference possible, based on our `initialValue`
    */
  inline def withPadding[A, R <: Atomic[A]](initialValue: A, padding: PaddingStrategy)(
    implicit builder: AtomicBuilder[A, R]): R =
    builder.buildInstance(initialValue, padding, allowPlatformIntrinsics = true)

  /** Returns the builder that would be chosen to construct Atomic
    * references for the given `initialValue`.
    */
  def builderFor[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): AtomicBuilder[A, R] =
    builder
}
