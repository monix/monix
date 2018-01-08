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

package monix.execution.schedulers

import monix.execution.UncaughtExceptionReporter
import scala.scalajs.js

/** Utils for quickly using Javascript's `setTimeout` and
  * `clearTimeout`.
  */
private[schedulers] object Timer {
  def setTimeout(delayMillis: Long, r: Runnable, reporter: UncaughtExceptionReporter): js.Dynamic = {
    val lambda: js.Function = () =>
      try { r.run() } catch { case t: Throwable =>
        reporter.reportFailure(t)
      }

    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  def clearTimeout(task: js.Dynamic): js.Dynamic = {
    js.Dynamic.global.clearTimeout(task)
  }
}
