/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/** Utils for quickly using Javascript's `setTimeout` and
  * `clearTimeout`.
  */
private[schedulers] object JSTimer {
  def setTimeout(ec: ExecutionContext, delayMillis: Long, r: Runnable): js.Dynamic = {
    val lambda: js.Function = () =>
      try {
        r.run()
      } catch { case t: Throwable => ec.reportFailure(t) }

    js.Dynamic.global.setTimeout(lambda, delayMillis.toDouble)
  }

  def clearTimeout(task: js.Dynamic): Unit = {
    js.Dynamic.global.clearTimeout(task)
    ()
  }
}
