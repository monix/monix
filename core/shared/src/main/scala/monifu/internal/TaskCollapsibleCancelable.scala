/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: https://monifu.org
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

package monifu.internal

import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.Cancelable
import monifu.concurrent.cancelables.BooleanCancelable
import scala.annotation.tailrec

private[monifu] final class TaskCollapsibleCancelable extends BooleanCancelable {
  import TaskCollapsibleCancelable.{State, Active, Cancelled}

  private[this] val state = Atomic(Active(Cancelable.empty) : State)
  private def underlying =
    state.get match {
      case Active(s) => s
      case _ => null
    }

  def isCanceled: Boolean = state.get match {
    case Cancelled => true
    case _ => false
  }

  @tailrec def cancel(): Boolean = state.get match {
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
  @tailrec def update(value: Cancelable): Unit =
    state.get match {
      case Cancelled => value.cancel()
      case current @ Active(s) =>
        val newState = value match {
          case ref: TaskCollapsibleCancelable =>
            if (ref.underlying == null)
              Cancelled
            else
              Active(ref.underlying)
          case _ =>
            Active(value)
        }

        if (!state.compareAndSet(current, newState))
          update(value)
    }
}

private[monifu] object TaskCollapsibleCancelable {
  def apply(): TaskCollapsibleCancelable =
    new TaskCollapsibleCancelable()

  def apply(s: Cancelable): TaskCollapsibleCancelable = {
    val ms = new TaskCollapsibleCancelable()
    ms() = s
    ms
  }

  private sealed trait State
  private case class Active(s: Cancelable) extends State
  private case object Cancelled extends State
}