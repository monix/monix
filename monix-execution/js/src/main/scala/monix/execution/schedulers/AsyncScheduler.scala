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

package monix.execution
package schedulers

import java.util.concurrent.TimeUnit

import monix.execution.schedulers.JSTimer.{ clearTimeout, setTimeout }
import monix.execution.{ ExecutionModel => ExecModel }

import scala.concurrent.ExecutionContext
import monix.execution.internal.InterceptRunnable

/** An `AsyncScheduler` schedules tasks to be executed asynchronously,
  * either now or in the future, by means of Javascript's `setTimeout`.
  */
final class AsyncScheduler private (
  context: ExecutionContext,
  override val properties: Properties,
  reporter: UncaughtExceptionReporter
) extends ReferenceScheduler with BatchingScheduler {

  protected def executeAsync(r: Runnable): Unit =
    context.execute {
      if (reporter ne null) InterceptRunnable(r, reporter)
      else r
    }

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val millis = {
      val v = TimeUnit.MILLISECONDS.convert(initialDelay, unit)
      if (v < 0) 0L else v
    }
    val task = setTimeout(context, millis, r)
    Cancelable(() => clearTimeout(task))
  }

  override def reportFailure(t: Throwable): Unit =
    if (reporter eq null) context.reportFailure(t)
    else reporter.reportFailure(t)

  override def withProperties(properties: Properties): AsyncScheduler =
    new AsyncScheduler(context, properties, reporter)

  override val features: Features =
    Features(Scheduler.BATCHING)
}

object AsyncScheduler {
  /** Builder for [[AsyncScheduler]].
    *
    * @param context is the `scala.concurrent.ExecutionContext` that gets used
    *        for executing `Runnable` values and for reporting errors
    *
    * @param executionModel is the preferred
    *        [[monix.execution.ExecutionModel ExecutionModel]], a guideline
    *        for run-loops and producers of data.
    */
  def apply(
    context: ExecutionContext,
    executionModel: ExecModel,
    r: UncaughtExceptionReporter = null
  ): AsyncScheduler =
    new AsyncScheduler(context, Properties[ExecModel](executionModel), r)
}
