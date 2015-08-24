/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.concurrent

import language.experimental.macros
import scala.concurrent.duration._

/**
 * Package provided for Scala.js for source-level compatibility.
 * Usage of these locks in Scala.js does not imply any overhead.
 */
package object locks {
  type SpinLock = LockImpl.type

  def SpinLock(): SpinLock =
    LockImpl

  /**
   * Provided for Scala.js for source-level compatibility purposes.
   * Usage does not imply any overhead.
   */
  private[locks] object LockImpl {
    def enter[T](cb: => T): T = macro Macros.lockMacroImpl[T]

    def enterInterruptibly[T](cb: => T): T = macro Macros.lockMacroImpl[T]

    def tryEnter[T](cb: => T): Boolean = macro Macros.tryLockMacro[T]

    def tryEnter[T](time: Long, unit: TimeUnit, cb: => T): Boolean = macro Macros.tryLockDurationMacro[T]
  }
}
