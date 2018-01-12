/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.tail.internal

import monix.eval.Task
import monix.execution.Scheduler
import monix.tail.Iterant
import monix.tail.Iterant.Suspend

import scala.concurrent.duration._

private[tail] object IterantIntervalAtFixedRate {
  def apply(initialDelay: FiniteDuration, interval: FiniteDuration): Iterant[Task, Long] = {
    def loop(index: Long, s: Scheduler): Iterant[Task, Long] = {
      val startTime = s.currentTimeMillis()
      Iterant.now(index) ++ Task.defer {
        val elapsed = (s.currentTimeMillis() - startTime).millis

        if (elapsed > interval) {
          Task.now(loop(index + 1, s))
        } else {
          Task.now(loop(index + 1, s)).delayExecution(interval - elapsed)
        }
      }
    }

    val loopTask = Task.deferAction { s =>
      Task.now(loop(0, s))
    }

    initialDelay match {
      case Duration.Zero =>
        Suspend(loopTask, Task.unit)
      case _ =>
        Suspend(loopTask.delayExecution(initialDelay), Task.unit)
    }
  }
}
