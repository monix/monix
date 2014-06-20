/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 * Represents a [[monifu.concurrent.Cancelable]] whose underlying cancelable reference can be swapped for another.
 *
 * Example:
 * {{{
 *   val s = MultiAssignmentCancelable()
 *   s() = c1 // sets the underlying cancelable to c1
 *   s() = c2 // swaps the underlying cancelable to c2
 *
 *   s.cancel() // also cancels c2
 *
 *   s() = c3 // also cancels c3, because s is already canceled
 * }}}
 */
final class MultiAssignmentCancelable private () extends BooleanCancelable {
  private[this] var _isCanceled = false
  private[this] var _subscription = Cancelable()
  private[this] val lock = Atomic(false)

  @tailrec
  def isCanceled: Boolean =
    if (!lock.compareAndSet(expect = false, update = true))
      isCanceled
    else
      try _isCanceled finally lock.set(update = false)

  @tailrec
  def cancel(): Unit =
    if (!lock.compareAndSet(expect = false, update = true))
      cancel()
    else if (_isCanceled)
      lock.set(update = false)
    else
      try _subscription.cancel() finally {
        _isCanceled = true
        _subscription = Cancelable.empty
        lock.set(update = false)
      }

  /**
   * Swaps the underlying cancelable reference with `s`.
   *
   * In case this `MultiAssignmentCancelable` is already canceled,
   * then the reference `value` will also be canceled on assignment.
   */
  @tailrec
  def update(value: Cancelable): Unit = {
    if (!lock.compareAndSet(expect = false, update = true))
      update(value)
    else if (_isCanceled)
      try value.cancel() finally lock.set(update = false)
    else
      try _subscription = value finally lock.set(update = false)
  }

  /**
   * Alias for `update(value)`
   */
  def `:=`(value: Cancelable): Unit =
    update(value)
}

object MultiAssignmentCancelable {
  def apply(): MultiAssignmentCancelable =
    new MultiAssignmentCancelable()

  def apply(s: Cancelable): MultiAssignmentCancelable = {
    val ms = new MultiAssignmentCancelable()
    ms() = s
    ms
  }
}