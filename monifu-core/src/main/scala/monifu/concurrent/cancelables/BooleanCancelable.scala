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
import monifu.concurrent.atomic.padded.Atomic

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
      @volatile private[this] var _isCanceled = false
      def isCanceled = _isCanceled

      def cancel(): Unit = {
        _isCanceled = true
      }
    }

  def apply(cb: => Unit): BooleanCancelable =
    new BooleanCancelable {
      private[this] val _isCanceled = Atomic(false)

      def isCanceled =
        _isCanceled.get

      def cancel(): Unit =
        if (_isCanceled.compareAndSet(expect=false, update=true)) {
          cb
        }
    }

  val alreadyCanceled: BooleanCancelable =
    new BooleanCancelable {
      val isCanceled = true
      def cancel() = ()
    }
}
