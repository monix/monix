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

package monix.execution.cancelables

import monix.execution.Cancelable
import monix.execution.atomic.{PaddingStrategy, AtomicAny}
import scala.annotation.tailrec

/** Represents a [[monix.execution.Cancelable]] whose underlying cancelable
  * reference can be swapped for another.
  *
  * Example:
  * {{{
  *   val s = MultiAssignmentCancelable()
  *   s := c1 // sets the underlying cancelable to c1
  *   s := c2 // swaps the underlying cancelable to c2
  *
  *   s.cancel() // also cancels c2
  *
  *   s := c3 // also cancels c3, because s is already canceled
  * }}}
  *
  * Also see [[SerialCancelable]], which is similar, except that it cancels the
  * old cancelable upon assigning a new cancelable.
  */
final class MultiAssignmentCancelable private (initial: Cancelable)
  extends AssignableCancelable.Multi {

  import MultiAssignmentCancelable.{State, Active, Cancelled}

  private[this] val state = {
    val ref = if (initial != null) initial else Cancelable.empty
    AtomicAny.withPadding(Active(ref,0) : State, PaddingStrategy.LeftRight128)
  }

  override def isCanceled: Boolean =
    state.get match {
      case Cancelled => true
      case _ => false
    }

  /** Returns the current order index, useful for working with
    * [[orderedUpdate]] in instances where the [[MultiAssignmentCancelable]]
    * reference is shared.
    */
  def currentOrder: Long =
    state.get match {
      case Cancelled => 0
      case Active(_, order) => order
    }

  override def cancel(): Unit = {
    // Using getAndSet, which on Java 8 should be faster than
    // a compare-and-set.
    val oldState = state.getAndSet(Cancelled)
    if (oldState ne Cancelled)
      oldState.asInstanceOf[Active].s.cancel()
  }

  @tailrec def `:=`(value: Cancelable): this.type =
    state.get match {
      case Cancelled =>
        value.cancel()
        this

      case current @ Active(_, currentOrder) =>
        if (!state.compareAndSet(current, Active(value, currentOrder)))
          :=(value) // retry
        else
          this
    }

  @tailrec def orderedUpdate(value: Cancelable, order: Long): this.type =
    state.get match {
      case Cancelled =>
        value.cancel()
        this

      case current @ Active(_, currentOrder) =>
        val sameSign = (currentOrder < 0) ^ (order >= 0)
        val isOrdered =
          (sameSign && currentOrder <= order) ||
          (currentOrder >= 0L && order < 0L) // takes overflow into account

        if (!isOrdered) this else {
          if (!state.compareAndSet(current, Active(value, order)))
            orderedUpdate(value, order) // retry
          else
            this
        }
    }
}

object MultiAssignmentCancelable {
  def apply(): MultiAssignmentCancelable =
    new MultiAssignmentCancelable(null)

  def apply(s: Cancelable): MultiAssignmentCancelable =
    new MultiAssignmentCancelable(s)

  private sealed trait State
  private final case class Active(s: Cancelable, order: Long)
    extends State
  private case object Cancelled
    extends State
}