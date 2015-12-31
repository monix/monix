/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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
 
package monix.concurrent.cancelables

import monix.concurrent.Cancelable
import asterix.atomic.padded.Atomic

/**
 * Represents a Cancelable that can queried for the canceled status.
 */
trait BooleanCancelable extends Cancelable {
  /**
   * @return true in case this cancelable hasn't been canceled.
   */
  def isCanceled: Boolean
}

object BooleanCancelable {
  def apply(): BooleanCancelable =
    new BooleanCancelable {
      private[this] val _isCanceled = Atomic(false)
      def isCanceled = _isCanceled.get

      def cancel(): Boolean = {
        _isCanceled.compareAndSet(expect = false, update = true)
      }
    }

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
    *     visibility after write is not guaranteed
    */
  def weak(): BooleanCancelable =
    monix.internal.concurrent.WeakBooleanCancelable()

  val alreadyCanceled: BooleanCancelable =
    new BooleanCancelable {
      val isCanceled = true
      def cancel() = false
    }
}
