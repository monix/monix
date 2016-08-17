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

import java.util.concurrent.{TimeUnit, ScheduledExecutorService}
import monix.execution.schedulers.AsyncScheduler.DeferredRunnable
import scala.concurrent.ExecutionContext
import monix.execution.{Cancelable, UncaughtExceptionReporter}

/** An `AsyncScheduler` schedules tasks to happen in the future with the
  * given `ScheduledExecutorService` and the tasks themselves are executed on
  * the given `ExecutionContext`.
  */
final class AsyncScheduler private (
  scheduler: ScheduledExecutorService,
  ec: ExecutionContext,
  r: UncaughtExceptionReporter,
  val executionModel: ExecutionModel)
  extends ReferenceScheduler with LocalBatchingExecutor {

  protected def executeAsync(r: Runnable): Unit =
    ec.execute(r)

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    if (initialDelay <= 0) {
      ec.execute(r)
      Cancelable.empty
    } else {
      val deferred = new DeferredRunnable(r, ec)
      val task = scheduler.schedule(deferred, initialDelay, unit)
      Cancelable(() => task.cancel(true))
    }
  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val deferred = new DeferredRunnable(r, ec)
    val task = scheduler.scheduleWithFixedDelay(deferred, initialDelay, delay, unit)
    Cancelable(() => task.cancel(false))
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val deferred = new DeferredRunnable(r, ec)
    val task = scheduler.scheduleAtFixedRate(deferred, initialDelay, period, unit)
    Cancelable(() => task.cancel(false))
  }

  override def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)
}

object AsyncScheduler {
  /** Builder for [[AsyncScheduler]].
    *
    * @param schedulerService is the Java `ScheduledExecutorService` that will take
    *        care of scheduling tasks for execution with a delay.
    * @param ec is the execution context that will execute all runnables
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    * @param executionModel is the preferred [[ExecutionModel]], a guideline
    *        for run-loops and producers of data.
    */
  def apply(
    schedulerService: ScheduledExecutorService,
    ec: ExecutionContext,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecutionModel): AsyncScheduler =
    new AsyncScheduler(schedulerService, ec, reporter, executionModel)

  /** Runnable that defers the execution of the given runnable to the
    * given execution context.
    */
  private class DeferredRunnable(r: Runnable, ec: ExecutionContext) extends Runnable {
    def run(): Unit = ec.execute(r)
  }
}
