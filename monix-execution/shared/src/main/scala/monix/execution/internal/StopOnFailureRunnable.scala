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

package monix.execution.internal

import monix.execution.{ Cancelable, UncaughtExceptionReporter }

import scala.util.control.NonFatal

/** Wrapper over `runnable` that hands the `Throwable` it throws to `reporter` and cancels `cancelable` to signal
 * the execution should stop. Fatal failures are re-thrown once reported.
 * Requires the executions of `runnable` to be sequential and assumes wrapped runnables won't be interrupted on
 * caller-invoked schedule cancel.
 *
 * @param cancelable used to signal that an exception occurred during the run and no further executions are expected
 */
private[internal] class StopOnFailureRunnable private (
  runnable: Runnable,
  reporter: UncaughtExceptionReporter,
  cancelable: Cancelable
) extends Runnable {

  @volatile private var failed = false

  override final def run(): Unit =
    if (!failed) {
      try runnable.run()
      catch {
        case e: Throwable =>
          try {
            failed = true
            reporter.reportFailure(e)
          } finally cancelable.cancel()
          if (!NonFatal(e)) throw e
      }
    }
}

private[internal] object StopOnFailureRunnable {
  def apply(runnable: Runnable, reporter: UncaughtExceptionReporter, cancelable: Cancelable): Runnable =
    new StopOnFailureRunnable(runnable, reporter, cancelable)
}
