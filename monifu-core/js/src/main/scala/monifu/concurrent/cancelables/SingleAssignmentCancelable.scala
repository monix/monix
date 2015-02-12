/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent.cancelables

import monifu.concurrent.Cancelable


/**
 * Represents a [[monifu.concurrent.Cancelable]] that can be assigned only once to another
 * cancelable reference.
 *
 * Similar to [[monifu.concurrent.cancelables.MultiAssignmentCancelable]], except that
 * in case of multi-assignment, it throws a `java.lang.IllegalStateException`.
 *
 * If the assignment happens after this cancelable has been canceled, then on
 * assignment the reference will get canceled too.
 *
 * Useful in case you need a forward reference.
 */
final class SingleAssignmentCancelable private () extends BooleanCancelable {
  import State._

  def isCanceled: Boolean = state match {
    case IsEmptyCanceled | IsCanceled =>
      true
    case _ =>
      false
  }

  /**
   * Sets the underlying cancelable reference with `s`.
   *
   * In case this `SingleAssignmentCancelable` is already canceled,
   * then the reference `value` will also be canceled on assignment.
   *
   * @throws IllegalStateException in case this cancelable has already been assigned
   */
  @throws(classOf[IllegalStateException])
  def update(value: Cancelable): Unit = state match {
    case Empty =>
      state = IsNotCanceled(value)
    case IsEmptyCanceled =>
      state = IsCanceled
      value.cancel()
    case IsCanceled | IsNotCanceled(_) =>
      throw new IllegalStateException(
        "Cannot assign to SingleAssignmentCancelable, as it was already assigned once"
      )
  }

  def cancel(): Unit = state match {
    case Empty =>
      state = IsEmptyCanceled
    case IsNotCanceled(cancelable) =>
      state = IsCanceled
      cancelable.cancel()
    case IsEmptyCanceled | IsCanceled =>
      () // do nothing
  }

  /**
   * Alias for `update(value)`
   */
  def `:=`(value: Cancelable): Unit =
    update(value)

  private[this] var state = Empty : State

  private[this] sealed trait State
  private[this] object State {
    case object Empty extends State
    case class IsNotCanceled(s: Cancelable) extends State
    case object IsCanceled extends State
    case object IsEmptyCanceled extends State
  }
}

object SingleAssignmentCancelable {
  def  apply(): SingleAssignmentCancelable =
    new SingleAssignmentCancelable()
}