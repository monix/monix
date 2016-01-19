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
import monix.base.atomic.padded.Atomic

/**
  * Represents a Cancelable that can queried for the canceled status.
  */
trait BooleanCancelable extends Cancelable {
  /** @return true in case this cancelable hasn't been canceled,
    *         or false otherwise.
    */
  def isCanceled: Boolean
}

object BooleanCancelable {
  /** Builder for [[BooleanCancelable]] */
  def apply(): BooleanCancelable =
    new BooleanCancelable {
      private[this] val _isCanceled = Atomic(false)
      def isCanceled = _isCanceled.get

      def cancel(): Boolean = {
        _isCanceled.compareAndSet(expect = false, update = true)
      }
    }

  /** Builder for [[BooleanCancelable]].
    *
    * @param callback is a function that will execute exactly once
    *                 on canceling.
    */
  def apply(callback: => Unit): BooleanCancelable =
    new BooleanCancelable {
      private[this] val _isCanceled = Atomic(false)

      def isCanceled =
        _isCanceled.get

      def cancel(): Boolean = {
        if (_isCanceled.compareAndSet(expect=false, update=true)) {
          callback
          true
        }
        else
          false
      }
    }

  /** Returns a weak version of [[BooleanCancelable]] that:
    *
    *   - always returns false on `cancel()`
    *   - its `isCanceled` field only has the writes ordered, so
    *     visibility after cancellation is not guaranteed
    */
  def weak(): BooleanCancelable =
    monix.execution.cancelables.WeakBooleanCancelable()

  /** Returns an instance of a [[BooleanCancelable]] that's
    * already canceled.
    */
  val alreadyCanceled: BooleanCancelable =
    new BooleanCancelable {
      val isCanceled = true
      def cancel() = false
    }
}
