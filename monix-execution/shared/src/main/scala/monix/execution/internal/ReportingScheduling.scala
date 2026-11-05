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
import monix.execution.cancelables.SingleAssignCancelable

/** Provides a harness for scheduling tasks that hand their own failures to an [[UncaughtExceptionReporter]]. */
private[execution] trait ReportingScheduling {

  /** The reporter that failures are handed to, or `null` to leave reporting to the underlying scheduler. */
  protected def reporterRef: UncaughtExceptionReporter

  protected final def schedule(runnable: Runnable)(scheduleRun: Runnable => Cancelable): Cancelable =
    if (reporterRef eq null) scheduleRun(runnable)
    else {
      val cancelable = SingleAssignCancelable()
      cancelable := scheduleRun(ReportingRunnable(runnable, reporterRef, cancelable))
      cancelable
    }

  protected final def schedulePeriodically(runnable: Runnable)(scheduleRun: Runnable => Cancelable): Cancelable =
    if (reporterRef eq null) scheduleRun(runnable)
    else {
      val cancelable = SingleAssignCancelable()
      // The task can fail before the schedule to cancel is assigned - given an almost immediate reschedule, a following
      // run could still be executed. An additional guard within `StopOnFailureRunnable` is needed.
      cancelable := scheduleRun(StopOnFailureRunnable(runnable, reporterRef, cancelable))
      cancelable
    }
}
