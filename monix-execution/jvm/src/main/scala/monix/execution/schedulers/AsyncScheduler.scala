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

import java.util.concurrent.{ ScheduledExecutorService, TimeUnit }

import monix.execution.{ Cancelable, ExecutionModel => ExecModel, Features, Scheduler, UncaughtExceptionReporter }

import scala.concurrent.ExecutionContext
import monix.execution.internal.{ InterceptRunnable, ScheduledExecutors }

/** An `AsyncScheduler` schedules tasks to happen in the future with the
  * given `ScheduledExecutorService` and the tasks themselves are executed on
  * the given `ExecutionContext`.
  */
final class AsyncScheduler private (
  scheduler: ScheduledExecutorService,
  ec: ExecutionContext,
  val properties: Properties,
  val executionModel: ExecModel,
  r: UncaughtExceptionReporter
) extends ReferenceScheduler with BatchingScheduler {

  protected def executeAsync(runnable: Runnable): Unit = {
    if (((r: AnyRef) eq ec) || (r eq null)) ec.execute(runnable)
    else ec.execute(InterceptRunnable(runnable, this))
  }

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable =
    ScheduledExecutors.scheduleOnce(this, scheduler)(initialDelay, unit, r)

  override def reportFailure(t: Throwable): Unit =
    if (r eq null) ec.reportFailure(t)
    else r.reportFailure(t)

  override def withProperties(properties: Properties): AsyncScheduler =
    new AsyncScheduler(scheduler, ec, properties, r)

  override val features: Features =
    Features(Scheduler.BATCHING)
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
    reporter: UncaughtExceptionReporter = null
  ): AsyncScheduler =
    new AsyncScheduler(schedulerService, ec, Properties(executionModel), reporter)
}
