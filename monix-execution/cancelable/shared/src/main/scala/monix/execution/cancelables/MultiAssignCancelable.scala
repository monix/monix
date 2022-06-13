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
import monix.execution.Cancelable.IsDummy
import monix.execution.atomic.{ AtomicAny, PaddingStrategy }

import scala.annotation.tailrec

/** Represents a [[monix.execution.Cancelable Cancelable]] whose
  * underlying cancelable reference can be swapped for another.
  *
  * Example:
  * {{{
  *   import monix.execution.Cancelable
  * 
  *   val s = MultiAssignCancelable()
  *   // sets the underlying cancelable
  *   s := Cancelable(() => println("cancel 1")) 
  *   // sets the underlying cancelable, forgetting about previous one
  *   s := Cancelable(() => println("cancel 2"))
  *
  *   s.cancel() // also cancels no.2
  *
  *   // also cancels no.3, because `s` is already canceled
  *   s := Cancelable(() => println("cancel 3")) 
  * }}}
  *
  * Also see:
  *
  *  - [[SerialCancelable]], which is similar, except that it
  *    cancels the old cancelable upon assigning a new cancelable
  *  - [[SingleAssignCancelable]] that is effectively a forward
  *    reference that can be assigned at most once
  *  - [[OrderedCancelable]] that's very similar with
  *    `MultiAssignCancelable`, but with the capability of forcing
  *    ordering on concurrent updates
  */
final class MultiAssignCancelable private (initial: Cancelable) extends AssignableCancelable.Multi {

  private[this] val state = {
    AtomicAny.withPadding(initial, PaddingStrategy.LeftRight128)
  }

  override def isCanceled: Boolean =
    state.get() match {
      case null => true
      case _ => false
    }

  override def cancel(): Unit = {
    // Using getAndSet, which on Java 8 should be faster than
    // a compare-and-set.
    val oldState: Cancelable = state.getAndSet(null)
    if (oldState ne null) oldState.cancel()
  }

  @tailrec def `:=`(value: Cancelable): this.type =
    state.get() match {
      case null =>
        value.cancel()
        this
      case `value` =>
        // ignore
        this
      case current =>
        if (state.compareAndSet(current, value)) {
          this
        } else {
          // $COVERAGE-OFF$
          :=(value) // retry
          // $COVERAGE-ON$
        }
    }

  /** Clears the underlying reference, setting it to a
    * [[Cancelable.empty]] (if not cancelled).
    *
    * This is equivalent with: `ref := Cancelable.empty`
    *
    * The purpose of this method is to release any underlying
    * reference for GC purposes, however if the underlying reference
    * is a [[monix.execution.Cancelable.IsDummy dummy]] then the
    * assignment doesn't happen because we don't care about releasing
    * dummy references.
    */
  @tailrec def clear(): Cancelable = {
    val current: Cancelable = state.get()
    if ((current ne null) && !current.isInstanceOf[IsDummy]) {
      if (state.compareAndSet(current, Cancelable.empty)) {
        current
      } else {
        // $COVERAGE-OFF$
        clear() // retry
        // $COVERAGE-ON$
      }
    } else {
      Cancelable.empty
    }
  }
}

object MultiAssignCancelable {
  /** Builder for [[MultiAssignCancelable]]. */
  def apply(): MultiAssignCancelable =
    new MultiAssignCancelable(Cancelable.empty)

  /** Builder for [[MultiAssignCancelable]]. */
  def apply(s: Cancelable): MultiAssignCancelable =
    new MultiAssignCancelable(s)
}
