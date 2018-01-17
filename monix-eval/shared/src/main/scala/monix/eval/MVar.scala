/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.eval

import monix.execution.atomic.PaddingStrategy
import monix.execution.misc.AsyncVar

/** A mutable location, that is either empty or contains
  * a value of type `A`.
  *
  * It has 2 fundamental atomic operations:
  *
  *  - [[put]] which fills the var if empty, or blocks
  *    (asynchronously) until the var is empty again
  *  - [[take]] which empties the var if full, returning the contained
  *    value, or blocks (asynchronously) otherwise until there is
  *    a value to pull
  *
  * The `MVar` is appropriate for building synchronization
  * primitives and performing simple inter-thread communications.
  * If it helps, it's similar with a `BlockingQueue(capacity = 1)`,
  * except that it doesn't block any threads, all waiting being
  * done asynchronously by means of [[Task]].
  *
  * Given its asynchronous, non-blocking nature, it can be used on
  * top of Javascript as well.
  *
  * Inspired by `Control.Concurrent.MVar` from Haskell and
  * by `scalaz.concurrent.MVar`.
  */
abstract class MVar[A] {
  /** Fills the `MVar` if it is empty, or blocks (asynchronously)
    * if the `MVar` is full, until the given value is next in
    * line to be consumed on [[take]].
    *
    * This operation is atomic.
    **
    * @return a task that on evaluation will complete when the
    *         `put` operation succeeds in filling the `MVar`,
    *         with the given value being next in line to
    *         be consumed
    */
  def put(a: A): Task[Unit]

  /** Empties the `MVar` if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @return a task that on evaluation will be completed after
    *         a value was retrieved
    */
  def take: Task[A]

  /** Tries reading the current value, or blocks (asynchronously)
    * until there is a value available, at which point the operation
    * resorts to a [[take]] followed by a [[put]].
    *
    * This `read` operation is equivalent to:
    * {{{
    *   for (a <- v.take; _ <- v.put(a)) yield a
    * }}}
    *
    * This operation is not atomic. Being equivalent with a `take`
    * followed by a `put`, in order to ensure that no race conditions
    * happen, additional synchronization is necessary.
    * See [[TaskSemaphore]] for a possible solution.
    *
    * @return a task that on evaluation will be completed after
    *         a value has been read
    */
  def read: Task[A]
}

/** Builders for [[MVar]]
  *
  * @define refTransparent [[Task]] returned by this operation
  *         produces a new [[MVar]] each time it is evaluated.
  *         To share a state between multiple consumers, pass
  *         [[MVar]] as a parameter or use [[Task.memoize]]
  */
object MVar {
  /** Builds an [[MVar]] instance with an `initial` value.
    *
    * $refTransparent
    */
  def apply[A](initial: A): Task[MVar[A]] =
    Task.eval(new AsyncMVarImpl[A](AsyncVar(initial)))

  /** Returns an empty [[MVar]] instance.
    *
    * $refTransparent
    */
  def empty[A]: Task[MVar[A]] =
    Task.eval(new AsyncMVarImpl[A](AsyncVar.empty))

  /** Builds an [[MVar]] instance with an `initial`  value and a given
    * [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    * (for avoiding the false sharing problem).
    *
    * $refTransparent
    */
  def withPadding[A](initial: A, ps: PaddingStrategy): Task[MVar[A]] =
    Task.eval(new AsyncMVarImpl[A](AsyncVar.withPadding(initial, ps)))

  /** Builds an empty [[MVar]] instance with a given
    * [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    * (for avoiding the false sharing problem).
    *
    * $refTransparent
    */
  def withPadding[A](ps: PaddingStrategy): Task[MVar[A]] =
    Task.eval(new AsyncMVarImpl[A](AsyncVar.withPadding(ps)))

  /** [[MVar]] implementation based on [[monix.execution.misc.AsyncVar]] */
  private final class AsyncMVarImpl[A](av: AsyncVar[A]) extends MVar[A] {
    def put(a: A): Task[Unit] =
      Task.unsafeCreate { (ctx, cb) =>
        val async = Callback.async(cb)(ctx.scheduler)
        // Execution could be synchronous
        if (av.unsafePut(a, async)) async.onSuccess(())
      }

    def take: Task[A] =
      Task.unsafeCreate { (ctx, cb) =>
        val async = Callback.async(cb)(ctx.scheduler)
        // Execution could be synchronous (e.g. result is null or not)
        av.unsafeTake(async) match {
          case null => () // do nothing
          case a => async.onSuccess(a)
        }
      }

    def read: Task[A] =
      Task.unsafeCreate { (ctx, cb) =>
        val async = Callback.async(cb)(ctx.scheduler)
        // Execution could be synchronous (e.g. result is null or not)
        av.unsafeRead(async) match {
          case null => () // do nothing
          case a => async.onSuccess(a)
        }
      }
  }
}
