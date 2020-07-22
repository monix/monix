/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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
import monix.execution.atomic.{AtomicAny, PaddingStrategy}
import scala.annotation.tailrec

/** Represents a [[monix.execution.Cancelable Cancelable]] whose
  * underlying cancelable reference can be swapped for another and
  * that has the capability to force ordering of updates.
  *
  * For the most part it's very similar with [[MultiAssignCancelable]]:
  * {{{
  *   val s = OrderedCancelable()
  *   s := c1 // sets the underlying cancelable to c1
  *   s := c2 // swaps the underlying cancelable to c2
  *
  *   s.cancel() // also cancels c2
  *
  *   s := c3 // also cancels c3, because s is already canceled
  * }}}
  *
  * However it also has the capability of doing
  * [[OrderedCancelable#orderedUpdate orderedUpdate]]:
  * {{{
  *   val s = OrderedCancelable()
  *
  *   ec.execute(new Runnable {
  *     def run() =
  *       s.orderedUpdate(ref2, 2)
  *   })
  *
  *   // The ordered updates are guarding against reversed ordering
  *   // due to the created thread being able to execute before the
  *   // following update
  *   s.orderedUpdate(ref1, 1)
  * }}}
  *
  * Also see:
  *
  *  - [[SerialCancelable]], which is similar, except that it
  *    cancels the old cancelable upon assigning a new cancelable
  *  - [[SingleAssignCancelable]] that is effectively a forward
  *    reference that can be assigned at most once
  *  - [[MultiAssignCancelable]] that's very similar with
  *    `OrderedCancelable`, but simpler, without the capability of
  *    doing ordered updates and possibly more efficient
  */
final class OrderedCancelable private (initial: Cancelable) extends AssignableCancelable.Multi {

  import OrderedCancelable.{Active, Cancelled, State}

  private[this] val state = {
    val ref = if (initial != null) initial else Cancelable.empty
    AtomicAny.withPadding(Active(ref, 0): State, PaddingStrategy.LeftRight128)
  }

  override def isCanceled: Boolean =
    state.get() match {
      case Cancelled => true
      case _ => false
    }

  /** Returns the current order index, useful for working with
    * [[orderedUpdate]] in instances where the [[OrderedCancelable]]
    * reference is shared.
    */
  def currentOrder: Long =
    state.get() match {
      case Cancelled => 0
      case Active(_, order) => order
    }

  override def cancel(): Unit = {
    // Using getAndSet, which on Java 8 should be faster than
    // a compare-and-set.
    val oldState = state.getAndSet(Cancelled)
    if (oldState ne Cancelled)
      oldState.asInstanceOf[Active].underlying.cancel()
  }

  @tailrec def `:=`(value: Cancelable): this.type =
    state.get() match {
      case Cancelled =>
        value.cancel()
        this

      case current @ Active(_, currentOrder) =>
        if (state.compareAndSet(current, Active(value, currentOrder))) {
          this
        } else {
          // $COVERAGE-OFF$
          :=(value) // retry
          // $COVERAGE-ON$
        }
    }

  /** An ordered update is an update with an order attached and if
    * the currently stored reference has on order that is greater
    * than the update, then the update is ignored.
    *
    * Useful to force ordering for concurrent updates.
    */
  @tailrec def orderedUpdate(value: Cancelable, order: Long): this.type =
    state.get() match {
      case Cancelled =>
        value.cancel()
        this

      case current @ Active(_, currentOrder) =>
        val sameSign = (currentOrder < 0) ^ (order >= 0)
        val isOrdered =
          (sameSign && currentOrder <= order) ||
            (currentOrder >= 0L && order < 0L) // takes overflow into account

        if (!isOrdered) this
        else {
          if (state.compareAndSet(current, Active(value, order))) {
            this
          } else {
            // $COVERAGE-OFF$
            orderedUpdate(value, order) // retry
            // $COVERAGE-ON$
          }
        }
    }
}

object OrderedCancelable {
  /** Builder for [[OrderedCancelable]]. */
  def apply(): OrderedCancelable =
    new OrderedCancelable(null)

  /** Builder for [[OrderedCancelable]]. */
  def apply(s: Cancelable): OrderedCancelable =
    new OrderedCancelable(s)

  /** Describes the internal state of [[OrderedCancelable]]. */
  private sealed abstract class State

  /** Non-canceled state, containing the current `underlying`
    * cancelable, along with an `order` index kept to discard
    * unordered updates.
    */
  private final case class Active(underlying: Cancelable, order: Long) extends State

  /** Internal [[State state]] signaling a cancellation occurred.
    *
    * After this state happens all subsequent assignments will
    * cancel the given values.
    */
  private case object Cancelled extends State
}
