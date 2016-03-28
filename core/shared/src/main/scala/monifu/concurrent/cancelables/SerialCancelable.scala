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
 */

package monifu.concurrent.cancelables

import monifu.concurrent.Cancelable
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec

/**
 * Represents a [[monifu.concurrent.Cancelable]] whose underlying cancelable
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
 *   s() = c3 // also cancels c3, because s is already canceled
 * }}}
 *
 * Also see [[MultiAssignmentCancelable]], which is similar, but doesn't cancel
 * the old cancelable upon assignment.
 */
final class SerialCancelable private () extends BooleanCancelable {
  import SerialCancelable.State
  import SerialCancelable.State._

  private[this] val state = Atomic(Active(Cancelable()) : State)

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
   * In case this `SerialCancelable` is already canceled,
   * then the reference `value` will also be canceled on assignment.
   */
  @tailrec
  def update(value: Cancelable): Unit = state.get match {
    case Cancelled => value.cancel()
    case current @ Active(s) =>
      if (!state.compareAndSet(current, Active(value)))
        update(value)
      else
        s.cancel()
  }

  /**
   * Alias for `update(value)`
   */
  def `:=`(value: Cancelable): Unit =
    update(value)
}

object SerialCancelable {
  def apply(): SerialCancelable =
    new SerialCancelable()

  def apply(s: Cancelable): SerialCancelable = {
    val ms = new SerialCancelable()
    ms() = s
    ms
  }

  private sealed trait State

  private object State {
    case class Active(s: Cancelable) extends State
    case object Cancelled extends State
  }
}