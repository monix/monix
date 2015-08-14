/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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
 
package monifu.concurrent.cancelables

import monifu.concurrent.atomic.{AtomicAny, Atomic}
import monifu.concurrent.Cancelable
import scala.annotation.tailrec


/**
 * Represents a composite of multiple cancelables. In case it is canceled, all
 * contained cancelables will be canceled too, e.g...
 * {{{
 *   val s = CompositeCancelable()
 *
 *   s += c1
 *   s += c2
 *   s += c3
 *
 *   // c1, c2, c3 will also be canceled
 *   s.cancel()
 * }}}
 *
 * Additionally, once canceled, on appending of new cancelable references, those
 * references will automatically get canceled too:
 * {{{
 *   val s = CompositeCancelable()
 *   s.cancel()
 *
 *   // c1 gets canceled, because s is already canceled
 *   s += c1
 *   // c2 gets canceled, because s is already canceled
 *   s += c2
 * }}}
 *
 * Adding and removing references from this composite is thread-safe.
 */
trait CompositeCancelable extends BooleanCancelable {
  /**
   * Adds a cancelable reference to this composite.
   */
  def add(s: Cancelable): Unit

  /**
   * Adds a cancelable reference to this composite.
   * This is an alias for `add()`.
   */
  final def +=(other: Cancelable): Unit = add(other)

  /**
   * Removes a cancelable reference from this composite.
   */
  def remove(s: Cancelable): Unit

  /**
   * Removes a cancelable reference from this composite.
   * This is an alias for `remove()`.
   */
  final def -=(s: Cancelable): Unit = remove(s)
}

object CompositeCancelable {
  def apply(): CompositeCancelable =
    new CompositeCancelableImpl

  def apply(head: Cancelable, tail: Cancelable*): CompositeCancelable = {
    val cs = new CompositeCancelableImpl
    cs += head; for (os <- tail) cs += os
    cs
  }

  private[this] final class CompositeCancelableImpl extends CompositeCancelable {
    def isCanceled =
      state.get.isCanceled

    @tailrec
    def cancel(): Boolean = {
      val oldState = state.get
      if (!oldState.isCanceled)
        if (!state.compareAndSet(oldState, State(Set.empty, isCanceled = true)))
          cancel()
        else {
          for (s <- oldState.subscriptions) s.cancel()
          true
        }
      else
        false
    }

    @tailrec
    def add(s: Cancelable): Unit = {
      val oldState = state.get
      if (oldState.isCanceled)
        s.cancel()
      else {
        val newState = oldState.copy(subscriptions = oldState.subscriptions + s)
        if (!state.compareAndSet(oldState, newState))
          add(s)
      }
    }

    @tailrec
    def remove(s: Cancelable): Unit = {
      val oldState = state.get
      if (!oldState.isCanceled) {
        val newState = oldState.copy(subscriptions = oldState.subscriptions - s)
        if (!state.compareAndSet(oldState, newState))
          remove(s)
      }
    }

    private[this] val state: AtomicAny[State] =
      Atomic(State())

    private[this] case class State(
      subscriptions: Set[Cancelable] = Set.empty,
      isCanceled: Boolean = false
    )
  }
}
