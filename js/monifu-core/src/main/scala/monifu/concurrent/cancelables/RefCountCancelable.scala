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
 * Represents a `Cancelable` that only executes the canceling logic when all
 * dependent cancelable objects have been canceled.
 *
 * After all dependent cancelables have been canceled, `onCancel` gets called.
 */
final class RefCountCancelable private (onCancel: () => Unit) extends BooleanCancelable {
  private[this] var canceled = false
  private[this] var activeCounter = 0

  def isCanceled: Boolean = {
    canceled
  }

  def acquire(): Cancelable = {
    if (canceled) {
      Cancelable.empty
    }
    else {
      activeCounter += 1

      Cancelable {
        activeCounter -= 1
        if (activeCounter == 0 && canceled)
          onCancel()
      }
    }
  }

  def cancel(): Unit = {
    if (!canceled) {
      canceled = true
      if (activeCounter == 0)
        onCancel()
    }
  }
}

object RefCountCancelable {
  def apply(onCancel: => Unit): RefCountCancelable =
    new RefCountCancelable(() => onCancel)
}
