/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.concurrent.schedulers

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import monifu.concurrent.Scheduler._
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.{Cancelable, UncaughtExceptionReporter}
import scala.concurrent.ExecutionContext


/**
 * An `AsyncScheduler` schedules tasks to happen in the future with the
 * given `ScheduledExecutorService` and the tasks themselves are executed on
 * the given `ExecutionContext`.
 */
final class AsyncScheduler private
    (s: ScheduledExecutorService, ec: ExecutionContext, r: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  override def scheduleOnce(r: Runnable): Cancelable = {
    val cancelable = BooleanCancelable.weak()
    val wrapped = new Runnable { def run() = if (!cancelable.isCanceled) r.run() }
    execute(wrapped)
    cancelable
  }

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable) = {
    if (initialDelay <= 0)
      scheduleOnce(r)
    else {
      val task = s.schedule(r, initialDelay, unit)
      Cancelable(task.cancel(true))
    }
  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleWithFixedDelay(r, initialDelay, delay, unit)
    Cancelable(task.cancel(false))
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleAtFixedRate(r, initialDelay, period, unit)
    Cancelable(task.cancel(false))
  }

  override def execute(runnable: Runnable): Unit =
    ec.execute(runnable)

  override def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

  override val env = Environment(512, Platform.JVM)
}

object AsyncScheduler {
  def apply(schedulerService: ScheduledExecutorService,
    ec: ExecutionContext, reporter: UncaughtExceptionReporter): AsyncScheduler =
    new AsyncScheduler(schedulerService, ec, reporter)
}
