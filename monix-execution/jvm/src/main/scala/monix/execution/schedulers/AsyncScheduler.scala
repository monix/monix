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
 *
 */

package monix.execution.schedulers

import java.util.concurrent.{TimeUnit, ScheduledExecutorService}
import scala.concurrent.ExecutionContext
import monix.execution.{Cancelable, UncaughtExceptionReporter}

/** An `AsyncScheduler` schedules tasks to happen in the future with the
  * given `ScheduledExecutorService` and the tasks themselves are executed on
  * the given `ExecutionContext`.
  */
private[schedulers] final class AsyncScheduler private
  (s: ScheduledExecutorService, ec: ExecutionContext, r: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    if (initialDelay <= 0) {
      execute(r)
      Cancelable.empty
    } else {
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
}

private[schedulers] object AsyncScheduler {
  def apply(schedulerService: ScheduledExecutorService,
    ec: ExecutionContext, reporter: UncaughtExceptionReporter): AsyncScheduler =
    new AsyncScheduler(schedulerService, ec, reporter)
}
