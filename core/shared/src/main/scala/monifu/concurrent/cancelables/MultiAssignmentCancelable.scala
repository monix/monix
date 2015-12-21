/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

import monifu.concurrent.Cancelable
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec

/**
 * Represents a [[monifu.concurrent.Cancelable]] whose underlying cancelable
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
 *
 * @param collapsible specifies that in case the update is also a
 *                    `MultiAssignmentCancelable`, then we should look at
 *                    the underlying cancelable and use that (if not null)
 */
final class MultiAssignmentCancelable private (collapsible: Boolean)
  extends BooleanCancelable {

  import MultiAssignmentCancelable.State
  import MultiAssignmentCancelable.State._

  private[this] val state = Atomic(Active(Cancelable()) : State)
  private def underlying =
    state.get match {
      case Active(s) => s
      case _ => null
    }

  def isCanceled: Boolean = state.get match {
    case Cancelled => true
    case _ => false
  }

  @tailrec
  def cancel(): Boolean = state.get match {
    case Cancelled => false
    case current @ Active(s) =>
      if (state.compareAndSet(current, Cancelled)) {
        s.cancel()
        true
      }
      else
        cancel()
  }

  /**
   * Swaps the underlying cancelable reference with `s`.
   *
   * In case this `MultiAssignmentCancelable` is already canceled,
   * then the reference `value` will also be canceled on assignment.
   */
  @tailrec
  def update(value: Cancelable): Unit =
    state.get match {
      case Cancelled => value.cancel()
      case current @ Active(s) =>
        val newState = if (!collapsible) value else
          value match {
            case ref: MultiAssignmentCancelable =>
              ref.underlying match {
                case null => ref
                case notNull => notNull
              }
            case _ =>
              value
          }

        if (!state.compareAndSet(current, Active(newState)))
          update(value)
    }

  /**
   * Alias for `update(value)`
   */
  def `:=`(value: Cancelable): Unit =
    update(value)
}

object MultiAssignmentCancelable {
  def apply(): MultiAssignmentCancelable =
    new MultiAssignmentCancelable(collapsible = false)

  def apply(s: Cancelable): MultiAssignmentCancelable = {
    val ms = new MultiAssignmentCancelable(collapsible = false)
    ms() = s
    ms
  }

  def collapsible(): MultiAssignmentCancelable = {
    new MultiAssignmentCancelable(collapsible = true)
  }

  def collapsible(s: Cancelable): MultiAssignmentCancelable = {
    val ms = new MultiAssignmentCancelable(collapsible = true)
    ms() = s
    ms
  }

  private sealed trait State
  private object State {
    case class Active(s: Cancelable) extends State
    case object Cancelled extends State
  }
}