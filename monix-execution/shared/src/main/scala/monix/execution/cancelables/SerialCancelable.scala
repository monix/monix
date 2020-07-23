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
  * Also see [[OrderedCancelable]], which is similar, but doesn't cancel
  * the old cancelable upon assignment.
  */
final class SerialCancelable private (initial: Cancelable) extends AssignableCancelable.Multi {

  private[this] val state = {
    AtomicAny.withPadding(initial, PaddingStrategy.LeftRight128)
  }

  override def isCanceled: Boolean =
    state.get() match {
      case null => true
      case _ => false
    }

  def cancel(): Unit =
    state.getAndSet(null) match {
      case null => () // nothing to do
      case ref => ref.cancel()
    }

  @tailrec def `:=`(value: Cancelable): this.type =
    state.get() match {
      case null =>
        value.cancel()
        this

      case current =>
        if (!state.compareAndSet(current, value)) {
          // $COVERAGE-OFF$
          :=(value) // retry
          // $COVERAGE-ON$
        } else {
          current.cancel()
          this
        }
    }
}

object SerialCancelable {
  /** Builder for [[SerialCancelable]]. */
  def apply(): SerialCancelable =
    apply(Cancelable.empty)

  /** Builder for [[SerialCancelable]]. */
  def apply(initial: Cancelable): SerialCancelable =
    new SerialCancelable(initial)
}
