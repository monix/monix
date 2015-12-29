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

package monifu.internal.concurrent

import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.misc.Unsafe

/** Optimized implementation of a simple [[BooleanCancelable]],
  * a cancelable that doesn't trigger any actions on cancel, it just
  * mutates its internal `isCanceled` field, which might be visible
  * at some point.
  *
  * Its `cancel()` always returns false, always.
  */
private[monifu] class WeakBooleanCancelable private (fieldOffset: Long)
  extends BooleanCancelable {

  private[this] var _isCanceled = 0
  def isCanceled: Boolean = {
    _isCanceled == 1
  }

  def cancel(): Boolean = {
    if (_isCanceled == 0)
      Unsafe.putOrderedInt(this, fieldOffset, 1)
    false
  }
}

private[monifu] object WeakBooleanCancelable {
  /** Builder for [[WeakBooleanCancelable]] */
  def apply(): BooleanCancelable = {
    new WeakBooleanCancelable(addressOffset)
  }

  /** Address offset of `SimpleBooleanCancelable#_isCanceled`,
    * needed in order to use `Unsafe.putOrderedInt`.
    */
  private[this] val addressOffset: Long = {
    Unsafe.objectFieldOffset(classOf[WeakBooleanCancelable]
      .getDeclaredFields.find(_.getName.endsWith("_isCanceled")).get)
  }
}
