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

package monix.execution
package schedulers

import java.util.concurrent.TimeUnit
import monix.execution.UncaughtExceptionReporter
import monix.execution.schedulers.Timer.{clearTimeout, setTimeout}
import monix.execution.{ExecutionModel => ExecModel}

/** An `AsyncScheduler` schedules tasks to be executed asynchronously,
  * either now or in the future, by means of Javascript's `setTimeout`.
  */
final class AsyncScheduler private (
  reporter: UncaughtExceptionReporter,
  override val executionModel: ExecModel)
  extends ReferenceScheduler with BatchingScheduler {

  protected def executeAsync(r: Runnable): Unit =
    setTimeout(0L, r, reporter)

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val millis = {
      val v = TimeUnit.MILLISECONDS.convert(initialDelay, unit)
      if (v < 0) 0L else v
    }

    val task = setTimeout(millis, r, reporter)
    Cancelable(() => clearTimeout(task))
  }

  override def reportFailure(t: Throwable): Unit =
    reporter.reportFailure(t)
  override def withExecutionModel(em: ExecModel): AsyncScheduler =
    new AsyncScheduler(reporter, em)
}

object AsyncScheduler {
  /** Builder for [[AsyncScheduler]].
    *
    * @param reporter is the [[UncaughtExceptionReporter]] that logs
    *        uncaught exceptions.
    * @param executionModel is the preferred
    *        [[monix.execution.ExecutionModel ExecutionModel]], a guideline
    *        for run-loops and producers of data.
    */
  def apply(reporter: UncaughtExceptionReporter,
    executionModel: ExecModel): AsyncScheduler =
    new AsyncScheduler(reporter, executionModel)
}

