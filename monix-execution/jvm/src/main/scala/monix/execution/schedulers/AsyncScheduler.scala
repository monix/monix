/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import monix.execution.{Cancelable, UncaughtExceptionReporter}
import monix.execution.{ExecutionModel => ExecModel}
import scala.concurrent.ExecutionContext

import monix.execution.internal.InterceptableRunnable

/** An `AsyncScheduler` schedules tasks to happen in the future with the
  * given `ScheduledExecutorService` and the tasks themselves are executed on
  * the given `ExecutionContext`.
  */
final class AsyncScheduler private (
  scheduler: ScheduledExecutorService,
  ec: ExecutionContext,
  val executionModel: ExecModel,
  r: UncaughtExceptionReporter)
  extends ReferenceScheduler with BatchingScheduler {

  protected def executeAsync(runnable: Runnable): Unit = {
    if (((r: AnyRef) eq ec) || (r eq null)) ec.execute(runnable)
    else ec.execute(InterceptableRunnable(runnable, this))
  }

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    if (initialDelay <= 0) {
      ec.execute(r)
      Cancelable.empty
    } else {
      val deferred = new ShiftedRunnable(r, this)
      val task = scheduler.schedule(deferred, initialDelay, unit)
      Cancelable(() => task.cancel(true))
    }
  }

  override def reportFailure(t: Throwable): Unit =
    if (r eq null) ec.reportFailure(t)
    else r.reportFailure(t)

  override def withExecutionModel(em: ExecModel): AsyncScheduler =
    new AsyncScheduler(scheduler, ec, em, r)
}

object AsyncScheduler {
  /** Builder for [[AsyncScheduler]].
    *
    * @param schedulerService is the Java `ScheduledExecutorService` that will take
    *        care of scheduling tasks for execution with a delay.
    * @param ec is the execution context that will execute all runnables
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    * @param executionModel is the preferred
    *        [[monix.execution.ExecutionModel ExecutionModel]],
    *        a guideline for run-loops and producers of data.
    */
  def apply(
    schedulerService: ScheduledExecutorService,
    ec: ExecutionContext,
    executionModel: ExecModel,
    reporter: UncaughtExceptionReporter = null): AsyncScheduler =
    new AsyncScheduler(schedulerService, ec, executionModel, reporter)
}
