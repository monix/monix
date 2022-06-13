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

/** Represents a [[monix.execution.Cancelable]] whose underlying cancelable
  * can be swapped for another cancelable which causes the previous underlying
  * cancelable to be canceled.
  *
  * Example:
  * {{{
  *   import monix.execution.Cancelable
  * 
  *   val s = SerialCancelable()
  *   // sets the underlying cancelable to #1
  *   s := Cancelable(() => println("cancel #1")) 
  *   // cancels c1 and swaps the underlying cancelable to #2
  *   s := Cancelable(() => println("cancel #2"))
  *
  *   s.cancel() // also cancels c2
  *
  *   // also cancels #3, because s is already canceled
  *   s := Cancelable(() => println("cancel #3"))
  * }}}
  *
  * Also see [[OrderedCancelable]], which is similar, but doesn't cancel
  * the old cancelable upon assignment.
  */
final class SerialCancelable private (initial: Cancelable) extends AssignableCancelable.Multi {

  private[this] val state =
    AtomicAny.withPadding(initial, PaddingStrategy.LeftRight128)

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
