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

package monix.execution.misc

import monix.execution.Listener
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.atomic.{AtomicAny, PaddingStrategy}

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{Future, Promise}

/** Asynchronous mutable location, that is either empty or contains
  * a value of type `A`.
  *
  * It has 2 fundamental atomic operations:
  *
  *  - [[put]] which fills the var if empty, or blocks
  *    (asynchronously) otherwise until the var is empty again
  *
  *  - [[take]] which empties the var if full, returning the contained
  *    value, or blocks (asynchronously) otherwise until there is
  *    a value to pull
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
  * Inspired by `Control.Concurrent.MVar` from Haskell.
  */
final class AsyncVar[A] private (_ref: AtomicAny[AsyncVar.State[A]]) {
  import AsyncVar._
  private[this] val stateRef: AtomicAny[State[A]] = _ref

  private def this(ps: PaddingStrategy) =
    this(AtomicAny.withPadding(AsyncVar.State.empty[A], ps))
  private def this(initial: A, ps: PaddingStrategy) =
    this(AtomicAny.withPadding(AsyncVar.State(initial), ps))

  /** Fills the `AsyncVar` if it is empty, or blocks (asynchronously)
    * if the `AsyncVar` is full, until the given value is next in
    * line to be consumed on [[take]].
    *
    * This operation is atomic.
    *
    * @see [[unsafePut]] for the raw, unsafe version that can work
    *     with plain callbacks.
    *
    * @return a future that will complete when the `put` operation
    *         succeeds in filling the `AsyncVar`, with the given
    *         value being next in line to be consumed
    */
  def put(a: A): Future[Unit] = {
    val p = Promise[Unit]()
    if (unsafePut(a, Listener.fromPromise(p))) Future.successful(())
    else p.future
  }

  /** Fills the `AsyncVar` if it is empty, or blocks (asynchronously)
    * if the `AsyncVar` is full, until the given value is next in
    * line to be consumed on [[take]] (or [[unsafeTake]]).
    *
    * This operation is atomic.
    *
    * @see [[put]] for the safe future-enabled version.
    *
    * @param a is the value to store
    * @param await is a callback that, only in case of asynchronous blocking,
    *        will get called when the blocking is over and the operation
    *        succeeded
    *
    * @return `true` if the operation succeeded already, with no
    *        blocking necessary, or `false` if the operation
    *        is blocked because the var is already full
    */
  @tailrec def unsafePut(a: A, await: Listener[Unit]): Boolean = {
    if (a == null) throw new NullPointerException("null not supported in AsyncVar/MVar")
    val current: State[A] = stateRef.get

    current match {
      case _: Empty[_] =>
        if (stateRef.compareAndSet(current, WaitForTake(a, Queue.empty))) true
        else unsafePut(a, await) // retry

      case WaitForTake(value, queue) =>
        val update = WaitForTake(value, queue.enqueue(a -> await))
        if (stateRef.compareAndSet(current, update)) false
        else unsafePut(a, await) // retry

      case current @ WaitForPut(first, queue) =>
        if (stateRef.compareAndSet(current, current.dequeue)) { first.onValue(a); true }
        else unsafePut(a, await) // retry
    }
  }

  /** Empties the var if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @see [[unsafeTake]] for the raw, unsafe version that can work
    *     with plain callbacks.
    */
  def take: Future[A] = {
    val p = Promise[A]()
    unsafeTake(Listener.fromPromise(p)) match {
      case null => p.future
      case a => Future.successful(a)
    }
  }

  /** Empties the var if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @see [[take]] for the safe future-enabled version.
    *
    * @param await is a callback that, only in case of asynchronous blocking,
    *        will get called sometime in the future with a value
    *
    * @return a value of type `A` if the operation succeeded already,
    *         with no blocking necessary, or `null` if async blocking
    *         is in progress (in which case the `await` callback
    *         gets called with the result)
    */
  @tailrec def unsafeTake(await: Listener[A]): A = {
    @inline def nil = null.asInstanceOf[A]

    val current: State[A] = stateRef.get
    current match {
      case _: Empty[_] =>
        if (stateRef.compareAndSet(current, WaitForPut(await, Queue.empty))) nil
        else unsafeTake(await) // retry

      case WaitForTake(value, queue) =>
        if (queue.isEmpty) {
          if (stateRef.compareAndSet(current, State.empty)) value
          else unsafeTake(await)
        }
        else {
          val ((ax, notify), xs) = queue.dequeue
          if (stateRef.compareAndSet(current, WaitForTake(ax, xs))) {
            notify.onValue(()) // notification
            value
          } else {
            unsafeTake(await) // retry
          }
        }

      case WaitForPut(first, queue) =>
        if (stateRef.compareAndSet(current, WaitForPut(first, queue.enqueue(await)))) nil
        else unsafeTake(await)
    }
  }

  /** Tries reading the current value, or blocks (asynchronously)
    * otherwise, until there is a value available, at which point
    * the operation resorts to a `take` followed by a `put`.
    *
    * This `read` operation is equivalent to:
    * {{{
    *   for (a <- v.take; _ <- v.put(a)) yield a
    * }}}
    *
    * This operation is not atomic. Being equivalent with a `take`
    * followed by a `put`, in order to ensure that no race conditions
    * happen, additional synchronization is necessary.
    * See [[AsyncSemaphore]] for a possible solution.
    *
    * @see [[unsafeRead]] for the raw, unsafe version that can work
    *     with plain callbacks.
    *
    * @return a future that might already be completed in case the
    *         result is available immediately
    */
  def read: Future[A] = {
    val p = Promise[A]()
    unsafeRead(Listener.fromPromise(p)) match {
      case null => p.future
      case a => Future.successful(a)
    }
  }

  /** Tries reading the current value, or blocks (asynchronously)
    * otherwise, until there is a value available, at which point
    * the operation resorts to a `take` followed by a `put`.
    *
    * This `read` operation is equivalent to:
    * {{{
    *   for (a <- v.take; _ <- v.put(a)) yield a
    * }}}
    *
    * This operation is not atomic. Being equivalent with a `take`
    * followed by a `put`, in order to ensure that no race conditions
    * happen, additional synchronization is necessary.
    * See [[AsyncSemaphore]] for a possible solution.
    *
    * @see [[read]] for the safe future-enabled version.
    *
    * @param await is a callback that, only in case of asynchronous blocking,
    *        will get called sometime in the future with a value
    *
    * @return a value of type `A` if the operation succeeded already,
    *         with no blocking necessary, or `null` if async blocking
    *         is in progress (in which case the `await` callback
    *         gets called with the result)
    */
  def unsafeRead(await: Listener[A]): A = {
    // To be used with unsafePut
    def awaitPut(a: A): Listener[Unit] = new Listener[Unit] {
      def onValue(value: Unit): Unit =
        await.onValue(a)
    }
    // To be used with unsafeTake
    def awaitTake: Listener[A] = new Listener[A] {
      def onValue(value: A): Unit = {
        // Execution could be synchronous
        if (unsafePut(value, awaitPut(value)))
          await.onValue(value)
      }
    }

    (stateRef.get : State[A]) match {
      case WaitForTake(value, _) => value // Fast-path
      case _ =>
        // Doing the equivalent of:
        // for (a <- take; _ <- put(a)) yield a
        unsafeTake(awaitTake) match {
          case null =>
            // Async execution
            null.asInstanceOf[A]
          case value =>
            if (unsafePut(value, awaitPut(value))) value
            else null.asInstanceOf[A] // Async execution
        }
    }
  }
}

object AsyncVar {
  /** Builds an [[AsyncVar]] instance with an `initial` value. */
  def apply[A](initial: A): AsyncVar[A] =
    new AsyncVar[A](initial, NoPadding)

  /** Returns an empty [[AsyncVar]] instance. */
  def empty[A]: AsyncVar[A] =
    new AsyncVar[A](NoPadding)

  /** Builds an [[AsyncVar]] instance with an `initial` value and a given
    * [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    * (for avoiding the false sharing problem).
    */
  def withPadding[A](initial: A, ps: PaddingStrategy): AsyncVar[A] =
    new AsyncVar[A](initial, ps)

  /** Builds an empty [[AsyncVar]] instance with a given
    * [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    * (for avoiding the false sharing problem).
    */
  def withPadding[A](ps: PaddingStrategy): AsyncVar[A] =
    new AsyncVar[A](ps)

  /** ADT modelling the internal state of [[AsyncVar]]. */
  private sealed trait State[A]

  /** Private [[State]] builders.*/
  private object State {
    private[this] val ref = Empty()
    def apply[A](a: A): State[A] = WaitForTake(a, Queue.empty)
    /** `Empty` state, reusing the same instance. */
    def empty[A]: State[A] = ref.asInstanceOf[State[A]]
  }

  /** `AsyncVar` state signaling an empty location.
    *
    * Evolves into [[WaitForPut]] or [[WaitForTake]],
    * depending on which operation happens first.
    */
  private final case class Empty[A]() extends State[A]

  /** `AsyncVar` state signaling it has `take` callbacks
    * registered and we are waiting for one or multiple
    * `put` operations.
    *
    * @param first is the first request waiting in line
    * @param queue are the rest of the requests waiting in line,
    *        if more than one `take` requests were registered
    */
  private final case class WaitForPut[A](first: Listener[A], queue: Queue[Listener[A]])
    extends State[A] {

    def dequeue: State[A] =
      if (queue.isEmpty) State.empty[A] else {
        val (x, xs) = queue.dequeue
        WaitForPut(x, xs)
      }
  }

  /** `AsyncVar` state signaling it has one or more values enqueued,
    * to be signaled on the next `take`.
    *
    * @param value is the first value to signal
    * @param queue are the rest of the `put` requests, along with the
    *        callbacks that need to be called whenever the corresponding
    *        value is first in line (i.e. when the corresponding `put`
    *        is unblocked from the user's point of view)
    */
  private final case class WaitForTake[A](value: A, queue: Queue[(A, Listener[Unit])])
    extends State[A]
}