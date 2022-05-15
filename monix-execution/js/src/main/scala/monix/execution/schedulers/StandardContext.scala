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

import monix.execution.UncaughtExceptionReporter
import scala.concurrent.ExecutionContext
import scala.scalajs.js

/** Internal API — An `ExecutionContext` implementation for JavaScript
  * that executes tasks using either `setImmediate` or `setTimeout`.
  */
private[execution] class StandardContext(reporter: UncaughtExceptionReporter) extends ExecutionContext {

  override def execute(r: Runnable): Unit = {
    executeRef(() =>
      try {
        r.run()
      } catch {
        case e: Throwable =>
          reporter.reportFailure(e)
      }
    )
    ()
  }

  override def reportFailure(cause: Throwable): Unit =
    reporter.reportFailure(cause)

  private[this] val executeRef: js.Dynamic = {
    if (js.typeOf(js.Dynamic.global.setImmediate) == "function")
      js.Dynamic.global.setImmediate
    else
      js.Dynamic.global.setTimeout
  }
}

private[execution] object StandardContext extends StandardContext(UncaughtExceptionReporter.default)
