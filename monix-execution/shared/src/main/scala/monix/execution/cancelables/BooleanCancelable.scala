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

import monix.execution.Cancelable
import monix.execution.atomic.AtomicAny

/** Represents a Cancelable that can be queried
  * for the canceled status.
  */
trait BooleanCancelable extends Cancelable {
  /** @return true in case this cancelable hasn't been canceled,
    *         or false otherwise.
    */
  def isCanceled: Boolean
}

object BooleanCancelable {
  /** Builder for [[BooleanCancelable]]. */
  def apply(): BooleanCancelable =
    new BooleanCancelable {
      @volatile private[this] var _isCanceled = false
      def isCanceled = _isCanceled

      def cancel(): Unit = {
        if (!_isCanceled) _isCanceled = true
      }
    }

  /** Builder for [[BooleanCancelable]].
    *
    * @param callback is a function that will execute exactly once
    *                 on canceling.
    */
  def apply(callback: () => Unit): BooleanCancelable =
    new BooleanCancelableTask(callback)

  /** Returns an instance of a [[BooleanCancelable]] that's
    * already canceled.
    */
  final val alreadyCanceled: BooleanCancelable =
    new BooleanCancelable with Cancelable.IsDummy {
      val isCanceled = true
      def cancel() = ()
    }

  /** Returns a [[BooleanCancelable]] that can never be canceled.
    *
    * Useful as a low-overhead instance whose `isCanceled` value
    * is always `false`, thus similar in spirit with [[alreadyCanceled]].
    */
  final val dummy: BooleanCancelable =
    new BooleanCancelable with Cancelable.IsDummy {
      val isCanceled = false
      def cancel() = ()
    }

  private final class BooleanCancelableTask(cb: () => Unit)
    extends BooleanCancelable {

    private[this] val callbackRef = AtomicAny(cb)
    def isCanceled: Boolean = callbackRef.get eq null

    def cancel(): Unit = {
      // Setting the callback to null with a `getAndSet` is solving
      // two problems: `cancel` is thus idempotent, plus we allow
      // the garbage collector to collect the task.
      val callback = callbackRef.getAndSet(null)
      if (callback != null) callback()
    }
  }
}
