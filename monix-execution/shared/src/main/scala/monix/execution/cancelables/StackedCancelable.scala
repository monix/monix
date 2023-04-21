/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.cancelables

import monix.execution.Cancelable
import monix.execution.atomic.{ AtomicAny, PaddingStrategy }
import scala.annotation.tailrec

/** Represents a composite of cancelables that are stacked,
  * so you can push a new reference, or pop an existing one and
  * when it gets canceled, then the whole stack gets canceled.
  *
  * Similar in spirit with [[CompositeCancelable]], except that
  * you can only pull out references in a FIFO fashion.
  *
  * Used in the implementation of `monix.eval.Task`.
  */
sealed abstract class StackedCancelable extends BooleanCancelable {
  /** Pops the head of the stack and pushes a list as an
    * atomic operation.
    *
    * This operation is the atomic equivalent of doing:
    * {{{
    *   sc.pop()
    *   sc.pushList(list)
    * }}}
    *
    * @param list is the list to prepend to the cancelable stack
    * @return the cancelable reference that was popped from the stack
    */
  def popAndPushList(list: List[Cancelable]): Cancelable

  /** Pops the head of the stack and pushes a list as
    * an atomic operation.
    *
    * This operation is the atomic equivalent of doing:
    * {{{
    *   sc.pop()
    *   sc.push(value)
    * }}}
    *
    * @param value is the cancelable reference to push on the stack
    * @return the cancelable reference that was popped from the stack
    */
  def popAndPush(value: Cancelable): Cancelable

  /** Pushes a whole list of cancelable references on the stack.
    *
    * This operation is the atomic equivalent of doing:
    * {{{
    *   for (c <- list.reverse) sc.push(c)
    * }}}
    *
    * @param list is the list to prepend to the cancelable stack
    */
  def pushList(list: List[Cancelable]): Unit

  /** Pushes a cancelable reference on the stack, to be
    * popped or cancelled later in FIFO order.
    */
  def push(value: Cancelable): Unit

  /** Removes a cancelable reference from the stack in FIFO order.
    *
    * @return the cancelable reference that was removed.
    */
  def pop(): Cancelable
}

object StackedCancelable {
  /** Builds an empty [[StackedCancelable]] reference. */
  def apply(): StackedCancelable =
    new Impl(Nil)

  /** Builds a [[StackedCancelable]] with an initial
    * cancelable reference in it.
    */
  def apply(initial: Cancelable): StackedCancelable =
    apply(List(initial))

  /** Builds a [[StackedCancelable]] already initialized with
    * a list of cancelable references, to be popped or canceled
    * later in FIFO order.
    */
  def apply(initial: List[Cancelable]): StackedCancelable =
    new Impl(initial)

  /** Reusable [[StackedCancelable]] reference that is already
    * cancelled.
    */
  val alreadyCanceled: StackedCancelable =
    new AlreadyCanceled

  /** Reusable [[StackedCancelable]] reference that cannot be
    * cancelled.
    */
  val uncancelable: StackedCancelable =
    new Uncancelable

  /** Implementation for [[StackedCancelable]] backed by a
    * cache-line padded atomic reference for synchronization.
    */
  private final class Impl(initial: List[Cancelable]) extends StackedCancelable {

    /**
      * Biasing the implementation for single threaded usage
      * in push/pop — this value is caching the last value seen,
      * in order to safe a `state.get` instruction before the
      * `compareAndSet` happens.
      */
    private[this] var cache = initial

    private[this] val state =
      AtomicAny.withPadding(initial, PaddingStrategy.LeftRight128)

    override def isCanceled: Boolean =
      state.get() == null

    override def cancel(): Unit = {
      // Using getAndSet, which on Java 8 should be faster than
      // a compare-and-set.
      state.getAndSet(null) match {
        case null => ()
        case list => Cancelable.cancelAll(list)
      }
    }

    @tailrec def popAndPushList(list: List[Cancelable]): Cancelable = {
      state.get() match {
        case null =>
          Cancelable.cancelAll(list)
          Cancelable.empty
        case Nil =>
          if (state.compareAndSet(Nil, list)) {
            Cancelable.empty
          } else {
            // $COVERAGE-OFF$
            popAndPushList(list)
            // $COVERAGE-ON$
          }
        case ref @ (head :: tail) =>
          if (state.compareAndSet(ref, concatList(list, tail))) {
            head
          } else {
            // $COVERAGE-OFF$
            popAndPushList(list)
            // $COVERAGE-ON$
          }
      }
    }

    @tailrec def popAndPush(value: Cancelable): Cancelable = {
      state.get() match {
        case null =>
          value.cancel()
          Cancelable.empty
        case Nil =>
          if (state.compareAndSet(Nil, value :: Nil)) {
            Cancelable.empty
          } else {
            // $COVERAGE-OFF$
            popAndPush(value)
            // $COVERAGE-ON$
          }
        case ref @ (head :: tail) =>
          if (state.compareAndSet(ref, value :: tail)) {
            head
          } else {
            // $COVERAGE-OFF$
            popAndPush(value)
            // $COVERAGE-ON$
          }
      }

    }

    @tailrec def pushList(list: List[Cancelable]): Unit = {
      state.get() match {
        case null =>
          Cancelable.cancelAll(list)
        case current =>
          if (!state.compareAndSet(current, concatList(list, current))) {
            // $COVERAGE-OFF$
            pushList(list) // retry
            // $COVERAGE-ON$
          }
      }
    }

    @tailrec
    private def pushLoop(current: List[Cancelable], value: Cancelable): Unit = {
      if (current eq null) {
        cache = null
        value.cancel()
      } else {
        val update = value :: current
        if (!state.compareAndSet(current, update))
          pushLoop(state.get(), value) // retry
        else
          cache = update
      }
    }

    def push(value: Cancelable): Unit =
      pushLoop(cache, value)

    @tailrec
    private def popLoop(current: List[Cancelable], isFresh: Boolean = false): Cancelable = {
      current match {
        case null | Nil =>
          if (isFresh) Cancelable.empty
          else popLoop(state.get(), isFresh = true)
        case ref @ (head :: tail) =>
          if (state.compareAndSet(ref, tail)) {
            cache = tail
            head
          } else {
            popLoop(state.get(), isFresh = true) // retry
          }
      }
    }

    def pop(): Cancelable =
      popLoop(cache)

    @tailrec
    private def concatList(list: List[Cancelable], current: List[Cancelable]): List[Cancelable] =
      list match {
        case Nil => current
        case x :: xs => concatList(xs, x :: current)
      }
  }

  /** [[StackedCancelable]] implementation that is already cancelled. */
  private final class AlreadyCanceled extends StackedCancelable {
    override def cancel(): Unit = ()
    override def isCanceled: Boolean = true
    override def pop(): Cancelable = Cancelable.empty

    override def push(value: Cancelable): Unit =
      value.cancel()

    override def popAndPushList(list: List[Cancelable]): Cancelable = {
      Cancelable.cancelAll(list)
      Cancelable.empty
    }

    override def popAndPush(value: Cancelable): Cancelable = {
      value.cancel()
      Cancelable.empty
    }

    override def pushList(list: List[Cancelable]): Unit =
      Cancelable.cancelAll(list)
  }

  /** [[StackedCancelable]] implementation that cannot be cancelled. */
  private final class Uncancelable extends StackedCancelable {
    override def cancel(): Unit = ()
    override def isCanceled: Boolean = false
    override def pop(): Cancelable = Cancelable.empty
    override def pushList(list: List[Cancelable]): Unit = ()
    override def push(value: Cancelable): Unit = ()
    override def popAndPushList(list: List[Cancelable]): Cancelable =
      Cancelable.empty
    override def popAndPush(value: Cancelable): Cancelable =
      Cancelable.empty
  }
}
