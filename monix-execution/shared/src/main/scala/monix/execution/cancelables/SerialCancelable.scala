/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
 *
 */

package monix.execution.cancelables

import monix.execution.Cancelable
import org.sincron.atomic.Atomic
import scala.annotation.tailrec

/** Represents a [[monix.execution.Cancelable]] whose underlying cancelable
  * can be swapped for another cancelable which causes the previous underlying
  * cancelable to be canceled.
  *
  * Example:
  * {{{
  *   val s = SerialCancelable()
  *   s := c1 // sets the underlying cancelable to c1
  *   s := c2 // cancels c1 and swaps the underlying cancelable to c2
  *
  *   s.cancel() // also cancels c2
  *
  *   s := c3 // also cancels c3, because s is already canceled
  * }}}
  *
  * Also see [[MultiAssignmentCancelable]], which is similar, but doesn't cancel
  * the old cancelable upon assignment.
  */
final class SerialCancelable private (initial: Cancelable)
  extends AssignableCancelable {

  import SerialCancelable.State
  import SerialCancelable.State._

  private[this] val state = {
    val s: State = Active(if (initial != null) initial else Cancelable.empty)
    Atomic(s)
  }

  override def isCanceled: Boolean =
    state.get match {
      case Cancelled => true
      case _ => false
    }

  @tailrec
  override def cancel(): Unit = state.get match {
    case Cancelled => () // nothing to do
    case current @ Active(s) =>
      if (state.compareAndSet(current, Cancelled))
        s.cancel()
      else
        cancel()
  }

  /** Swaps the underlying cancelable reference with the new `value`
    * and cancels the old cancelable that was replaced.
    *
    * In case this `SerialCancelable` is already canceled,
    * then the reference `value` will also be canceled on assignment.
    */
  @tailrec
  override def :=(value: Cancelable): this.type = state.get match {
    case Cancelled =>
      value.cancel()
      this
    case current @ Active(s) =>
      if (!state.compareAndSet(current, Active(value)))
        :=(value)
      else {
        s.cancel()
        this
      }
  }
}

object SerialCancelable {
  /** Builder for [[SerialCancelable]]. */
  def apply(): SerialCancelable =
    new SerialCancelable(null)

  /** Builder for [[SerialCancelable]]. */
  def apply(initial: Cancelable): SerialCancelable =
    new SerialCancelable(initial)

  private sealed trait State
  private object State {
    case class Active(s: Cancelable) extends State
    case object Cancelled extends State
  }
}