/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import java.util.concurrent.ScheduledExecutorService
import monix.execution.{Cancelable, Scheduler, UncaughtExceptionReporter}
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

/** An [[ExecutorScheduler]] is for building a
  * [[monix.execution.Scheduler Scheduler]] out of
  * a `ScheduledExecutorService`.
  */
final class ExecutorScheduler private (
  s: ScheduledExecutorService,
  r: UncaughtExceptionReporter,
  override val executionModel: ExecutionModel)
  extends ReferenceScheduler with BatchingExecutor {

  def executor: ScheduledExecutorService = s

  def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable = {
    scheduleOnce(initialDelay.length, initialDelay.unit, r)
  }

  def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable) = {
    if (initialDelay <= 0) {
      execute(r)
      Cancelable.empty
    }
    else {
      val task = s.schedule(r, initialDelay, unit)
      Cancelable(() => task.cancel(true))
    }
  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleWithFixedDelay(r, initialDelay, delay, unit)
    Cancelable(() => task.cancel(false))
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleAtFixedRate(r, initialDelay, period, unit)
    Cancelable(() => task.cancel(false))
  }

  def executeAsync(runnable: Runnable): Unit =
    s.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

  override def withExecutionModel(em: ExecutionModel): Scheduler =
    new ExecutorScheduler(s, r, em)
}

object ExecutorScheduler {
  /** Builder for [[AsyncScheduler]].
    *
    * @param schedulerService is the Java `ScheduledExecutorService` that will take
    *        care of scheduling and execution of all runnables.
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    * @param executionModel is the preferred [[ExecutionModel]], a guideline
    *        for run-loops and producers of data.
    */
  def apply(
    schedulerService: ScheduledExecutorService,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecutionModel): ExecutorScheduler =
    new ExecutorScheduler(schedulerService, reporter, executionModel)
}