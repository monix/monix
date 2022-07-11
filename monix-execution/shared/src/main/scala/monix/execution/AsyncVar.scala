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

package monix.execution

import monix.execution.annotations.{ UnsafeBecauseImpure, UnsafeProtocol }
import monix.execution.atomic.PaddingStrategy
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.GenericVar
import monix.execution.internal.GenericVar.Id
import scala.concurrent.Promise

/** Asynchronous mutable location, that is either empty or contains
  * a value of type `A`.
  *
  * It has these fundamental atomic operations:
  *
  *  - [[put]] which fills the var if empty, or waits
  *    (asynchronously) otherwise until the var is empty again
  *    (with the [[putByCallback]] overload)
  *
  *  - [[tryPut]] which fills the var if empty, returning `true`
  *    if it succeeded, or returning immediately `false` in case
  *    the var was full and thus the operation failed
  *
  *  - [[take]] which empties the var if full, returning the contained
  *    value, or waits (asynchronously) otherwise until there is
  *    a value to pull (with the [[takeByCallback]] overload)
  *
  *  - [[tryTake]] which empties the var if full, returning the
  *    contained value immediately as `Some(a)`, or otherwise returning
  *    `None` in case the var was empty and thus the operation failed
  *
  *  - [[read]] which reads the var if full, but without taking it
  *    from the interval var, or waits (asynchronously) until
  *    there is a value to read
  *
  *  - [[tryRead]] tries reading the var without modifying it in
  *    any way; if full then returns `Some(a)`, or `None` if empty
  *
  * The `AsyncVar` is appropriate for building synchronization
  * primitives and performing simple inter-thread communications.
  * If it helps, it's similar with a `BlockingQueue(capacity = 1)`,
  * except that it doesn't block any threads, all waiting being
  * callback-based.
  *
  * Given its asynchronous, non-blocking nature, it can be used on
  * top of Javascript as well.
  *
  * This is inspired by
  * [[https://hackage.haskell.org/package/base/docs/Control-Concurrent-MVar.html Control.Concurrent.MVar]]
  * from Haskell, except that the implementation is made to work with
  * plain Scala futures (and is thus impure).
  *
  * @define awaitParam is a callback that will be called when the
  *         operation succeeded with a result
  *
  * @define cancelableReturn a cancelable token that can be used to cancel
  *         the computation to avoid memory leaks in race conditions
  */
final class AsyncVar[A] private (initial: Option[A], ps: PaddingStrategy)
  extends GenericVar[A, Cancelable](initial, ps) {

  private def this(ps: PaddingStrategy) =
    this(None, ps)
  private def this(initial: A, ps: PaddingStrategy) =
    this(Some(initial), ps)

  override protected def makeCancelable(f: Id => Unit, id: Id): Cancelable =
    new Cancelable { def cancel() = f(id) }
  override protected def emptyCancelable: Cancelable =
    Cancelable.empty

  /** Fills the `AsyncVar` if it is empty, or blocks (asynchronously)
    * if the `AsyncVar` is full, until the given value is next in
    * line to be consumed on [[take]].
    *
    * This operation is atomic.
    *
    * @see [[putByCallback]] for the raw, unsafe version that can work with
    *      plain callbacks.
    *
    * @return a future that will complete when the `put` operation
    *         succeeds in filling the `AsyncVar`, with the given
    *         value being next in line to be consumed; note that this
    *         is a cancelable future that can be canceled to avoid
    *         memory leaks in race conditions
    */
  @UnsafeBecauseImpure
  def put(a: A): CancelableFuture[Unit] = {
    val p = Promise[Unit]()
    val c = putByCallback(a, Callback.fromPromise(p))
    CancelableFuture(p.future, c)
  }

  /** Fills the `AsyncVar` if it is empty, or blocks (asynchronously)
    * if the `AsyncVar` is full, until the given value is next in
    * line to be consumed on [[take]].
    *
    * This operation is atomic.
    *
    * @see [[put]] for the safe future-enabled version.
    *
    * @param a is the value to store
    * @param await $awaitParam
    * @return $cancelableReturn
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def putByCallback(a: A, await: Callback[Nothing, Unit]): Cancelable =
    unsafePut(a, await)

  /**
    * Tries to put a value in the underlying var, returning `true` if the
    * operation succeeded and thus the var was empty, or `false` if the
    * var was full and thus the operation failed.
    *
    * @see [[put]] for the version that can asynchronously wait for the
    *      var to become empty
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryPut(a: A): Boolean = unsafeTryPut(a)

  /** Empties the var if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @see [[takeByCallback]] for the raw, unsafe version that can work
    *     with plain callbacks.
    */
  @UnsafeBecauseImpure
  def take(): CancelableFuture[A] = {
    val p = Promise[A]()
    val c = takeByCallback(Callback.fromPromise(p))
    CancelableFuture(p.future, c)
  }

  /** Empties the var if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @see [[take]] for the safe future-enabled version.
    *
    * @param await $awaitParam
    * @return $cancelableReturn
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def takeByCallback(await: Callback[Nothing, A]): Cancelable =
    unsafeTake(await)

  /**
    * Tries to take a value from the underlying var, returning `Some(a)` if the
    * operation succeeded and thus the var was full, or `None` if the
    * var was empty and thus the operation failed.
    *
    * @see [[take]] for the version that can asynchronously wait for the
    *      var to become full
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryTake(): Option[A] = unsafeTryTake()

  /** Tries reading the current value, or waits (asynchronously)
    * until there is a value available.
    *
    * This operation is atomic.
    *
    * @see [[readByCallback]] for the raw, unsafe version that can work
    *     with plain callbacks.
    *
    * @return a future that might already be completed in case the
    *         result is available immediately
    */
  @UnsafeBecauseImpure
  def read(): CancelableFuture[A] = {
    val p = Promise[A]()
    val c = readByCallback(Callback.fromPromise(p))
    CancelableFuture(p.future, c)
  }

  /** Tries reading the current value, or waits (asynchronously)
    * until there is a value available.
    *
    * This operation is atomic.
    *
    * @see [[read]] for the safe future-enabled version.
    *
    * @param await $awaitParam
    * @return $cancelableReturn
    */
  @UnsafeProtocol @UnsafeBecauseImpure
  def readByCallback(await: Callback[Nothing, A]): Cancelable =
    unsafeRead(await)

  /**
    * Tries reading the current value, without modifying the var in any way:
    *
    *  - if full, returns `Some(a)`
    *  - if empty, returns `None`
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryRead(): Option[A] = unsafeTryRead()

  /**
    * Returns `true` if the var is empty, `false` otherwise.
    *
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def iEmpty(): Boolean = unsafeIsEmpty()
}

object AsyncVar {
  /** Builds an [[AsyncVar]] instance with an `initial` value.
    *
    * @param initial is the initial value, this var being initialized
    *        full; see [[empty]] for the alternative
    *
    * @param ps is an optional padding strategy for avoiding the
    *        "false sharing problem", a common JVM effect when multiple threads
    *        read and write in shared variables
    */
  def apply[A](initial: A, ps: PaddingStrategy = NoPadding): AsyncVar[A] =
    new AsyncVar[A](initial, ps)

  /** Returns an empty [[AsyncVar]] instance.
    *
    * @param ps is an optional padding strategy for avoiding the
    *        "false sharing problem", a common JVM effect when multiple threads
    *        read and write in shared variables
    */
  def empty[A](ps: PaddingStrategy = NoPadding): AsyncVar[A] =
    new AsyncVar[A](ps)
}
