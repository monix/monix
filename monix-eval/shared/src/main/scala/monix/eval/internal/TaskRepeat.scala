/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.eval.internal

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration

private[eval] object TaskRepeat {
  /** Implementation for `Task.repeatAtFixedRate` */
  def atFixedRate(initialDelay: FiniteDuration, period: FiniteDuration, task: Task[_]): Task[Nothing] = {
    import concurrent.duration._

    def loop(task: Task[_], periodMs: Long)(implicit s: Scheduler): Task[Nothing] =
      Task.defer {
        val startedAt = s.currentTimeMillis()

        task.flatMap { _ =>
          val duration = s.currentTimeMillis() - startedAt
          val nextDelay = {
            val v = periodMs - duration
            if (v < 0) 0 else v
          }

          val cycle = loop(task, periodMs)
          if (nextDelay <= 0) cycle
          else cycle.delayExecution(nextDelay.millis)
        }
      }

    // Need an `unsafeCreate` only because we need
    // the `Scheduler` to give us the current time
    Task.unsafeCreate { (context, cb) =>
      implicit val s = context.scheduler
      val t = loop(task, period.toMillis).delayExecution(initialDelay)
        // Loop is cancelable, meaning that if `isCanceled` is observed
        // then loop is stopped, although this might not be necessary
        // due to the behavior of `delayExecution`, added for good measure
        .executeWithOptions(_.enableAutoCancelableRunLoops)

      // Go, go, go
      Task.unsafeStartAsync(t, context, cb)
    }
  }

  /** Implementation for `Task.repeatWithFixedDelay` */
  def withFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration, task: Task[_]): Task[Nothing] = {
    import concurrent.duration._

    def loop(delay: FiniteDuration, task: Task[_]): Task[Nothing] =
      task.flatMap { _ =>
        loop(delay, task).delayExecution(delay)
      }

    loop(delay, task).delayExecution(initialDelay)
      .executeWithOptions(_.enableAutoCancelableRunLoops)
  }
}
