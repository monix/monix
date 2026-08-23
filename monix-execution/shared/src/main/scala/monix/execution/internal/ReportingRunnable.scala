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

import monix.execution.UncaughtExceptionReporter
import monix.execution.cancelables.BooleanCancelable

import scala.util.control.NonFatal

/** Wrapper over `runnable` that hands the `Throwable` it throws to `reporter`, unless `cancelable` is canceled.
 * A fatal failure is re-thrown once reported or ignored.
 *
 * @param cancelable indicates whether the runnable has been canceled
 */
private[internal] class ReportingRunnable private (
  runnable: Runnable,
  reporter: UncaughtExceptionReporter,
  cancelable: BooleanCancelable
) extends Runnable {

  override final def run(): Unit =
    try runnable.run()
    catch {
      case e: Throwable =>
        // don't report exceptions after cancellation, mirroring what AdaptedThreadPoolExecutor does
        if (!cancelable.isCanceled) reporter.reportFailure(e)
        if (!NonFatal(e)) throw e
    }
}

private[internal] object ReportingRunnable {
  def apply(runnable: Runnable, reporter: UncaughtExceptionReporter, cancelable: BooleanCancelable): Runnable =
    new ReportingRunnable(runnable, reporter, cancelable)
}
