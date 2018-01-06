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

import monix.execution.cancelables.SingleAssignmentCancelable.State
import monix.execution.atomic.AtomicAny
import scala.annotation.tailrec
import monix.execution.Cancelable

/** Represents a [[monix.execution.Cancelable]] that can be assigned only
  * once to another cancelable reference.
  *
  * Similar to [[monix.execution.cancelables.MultiAssignmentCancelable]],
  * except that in case of multi-assignment, it throws a
  * `java.lang.IllegalStateException`.
  *
  * If the assignment happens after this cancelable has been canceled, then on
  * assignment the reference will get canceled too.
  *
  * Useful in case you need a forward reference.
  */
final class SingleAssignmentCancelable private (extra: Cancelable)
  extends AssignableCancelable.Bool {

  // For binary compatibility
  private[SingleAssignmentCancelable] def this() = this(null)

  import State._

  override def isCanceled: Boolean =
    state.get match {
      case IsEmptyCanceled | IsCanceled =>
        true
      case _ =>
        false
    }

  /** Sets the underlying cancelable reference with `s`.
    *
    * In case this `SingleAssignmentCancelable` is already canceled,
    * then the reference `value` will also be canceled on assignment.
    *
    * Throws `IllegalStateException` in case this cancelable has already
    * been assigned.
    *
    * @return `this`
    */
  @throws(classOf[IllegalStateException])
  override def `:=`(value: Cancelable): this.type = {
    // Using getAndSet, which on Java 8 should be faster than
    // a compare-and-set.
    val oldState = state.getAndSet(IsActive(value))

    oldState match {
      case Empty => ()
      case IsEmptyCanceled => value.cancel()
      case IsCanceled | IsActive(_) =>
        value.cancel()
        raiseError()
    }

    this
  }

  @tailrec
  override def cancel(): Unit = {
    state.get match {
      case IsCanceled | IsEmptyCanceled => ()
      case IsActive(s) =>
        state.set(IsCanceled)
        if (extra != null) extra.cancel()
        s.cancel()
      case Empty =>
        if (!state.compareAndSet(Empty, IsEmptyCanceled))
          cancel()
        else if (extra != null)
          extra.cancel()
    }
  }

  private def raiseError(): Nothing = {
    throw new IllegalStateException(
      "Cannot assign to SingleAssignmentCancelable, " +
      "as it was already assigned once")
  }

  private[this] val state = AtomicAny(Empty : State)
}

object SingleAssignmentCancelable {
  /** Builder for [[SingleAssignmentCancelable]]. */
  def  apply(): SingleAssignmentCancelable =
    new SingleAssignmentCancelable()

  /** Builder for [[SingleAssignmentCancelable]] that takes an extra reference,
    * to be canceled on [[SingleAssignmentCancelable.cancel cancel()]]
    * along with whatever underlying reference we have.
    *
    * {{{
    *   val c = {
    *     val extra = Cancelable(() => println("extra canceled")
    *     SingleAssignmentCancelable.withExtra(extra)
    *   }
    *
    *   c := Cancelable(() => println("main canceled"))
    *
    *   // ...
    *   c.cancel()
    *   //=> extra canceled
    *   //=> main canceled
    * }}}
    */
  def plusOne(guest: Cancelable): SingleAssignmentCancelable =
    new SingleAssignmentCancelable(guest)

  private[monix] sealed trait State
  private[monix] object State {
    case object Empty extends State
    case class IsActive(s: Cancelable) extends State
    case object IsCanceled extends State
    case object IsEmptyCanceled extends State
  }
}