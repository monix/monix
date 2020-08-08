/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import java.util.concurrent.ScheduledExecutorService
import monix.execution.schedulers.ShiftedRunnable
import monix.execution.{Cancelable, Scheduler}
import scala.concurrent.duration.TimeUnit

private[execution] object ScheduledExecutors {
  /**
    * Reusable implementation for a `scheduleOnce` that uses
    * an underlying Java `ScheduledExecutorService`.
    */
  def scheduleOnce(
    executor: Scheduler,
    scheduler: ScheduledExecutorService
  )(
    initialDelay: Long,
    unit: TimeUnit,
    r: Runnable
  ): Cancelable = {
    if (initialDelay <= 0) {
      executor.execute(r)
      Cancelable.empty
    } else {
      val deferred = new ShiftedRunnable(r, executor)
      val task = scheduler.schedule(deferred, initialDelay, unit)
      Cancelable(() => { task.cancel(true); () })
    }
  }
}
