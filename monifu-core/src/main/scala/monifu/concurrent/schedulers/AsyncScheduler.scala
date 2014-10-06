/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.concurrent.schedulers

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.{UncaughtExceptionReporter, Scheduler, Cancelable}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


/**
 * An `AsyncScheduler` schedules tasks to happen in the future with the
 * given `ScheduledExecutorService` and the tasks themselves are executed on
 * the given `ExecutionContext`.
 */
final class AsyncScheduler private
    (s: ScheduledExecutorService, ec: ExecutionContext, r: UncaughtExceptionReporter)
  extends Scheduler {

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable =
    if (initialDelay <= Duration.Zero)
      scheduleOnce(action)
    else {
      val sub = SingleAssignmentCancelable()

      val runnable = new Runnable {
        def run(): Unit =
          ec.execute(new Runnable {
            def run(): Unit =
              if (!sub.isCanceled) action
          })
      }

      val task =
        if (initialDelay < oneHour)
          s.schedule(runnable, initialDelay.toNanos, TimeUnit.NANOSECONDS)
        else
          s.schedule(runnable, initialDelay.toMillis, TimeUnit.MILLISECONDS)

      sub := Cancelable(task.cancel(true))
      sub
    }

  /**
   * Overwritten for performance reasons.
   */
  override def scheduleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration, action: => Unit): Cancelable = {
    @volatile var isCanceled = false
    val runnable = new Runnable {
      def run(): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            if (!isCanceled) action
        })
    }

    val task = s.scheduleWithFixedDelay(runnable, initialDelay.toMillis, delay.toMillis, TimeUnit.MILLISECONDS)
    Cancelable { isCanceled = true; task.cancel(false) }
  }

  def execute(runnable: Runnable): Unit =
    ec.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

  private[this] val oneHour = 1.hour
}

object AsyncScheduler {
  def apply(schedulerService: ScheduledExecutorService,
            ec: ExecutionContext, reporter: UncaughtExceptionReporter): AsyncScheduler =
    new AsyncScheduler(schedulerService, ec, reporter)
}
