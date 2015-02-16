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

package monifu.concurrent

import monifu.concurrent.schedulers.AsyncScheduler

/**
 * Defines implicit values that can be used by importing in the
 * current context.
 *
 * Example:
 * {{{
 *   import monifu.concurrent.Implicits.globalScheduler
 * }}}
 */
object Implicits {
  /**
   * A global [[Scheduler]] instance, provided for convenience, piggy-backing
   * on top of `global.setTimeout`.
   */
  implicit lazy val globalScheduler: Scheduler =
    AsyncScheduler(UncaughtExceptionReporter.LogExceptionsToStandardErr)
}
