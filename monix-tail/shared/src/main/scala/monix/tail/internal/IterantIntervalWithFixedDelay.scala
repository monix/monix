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
import monix.tail.Iterant
import monix.tail.Iterant.Suspend
import scala.concurrent.duration.{Duration, FiniteDuration}

private[tail] object IterantIntervalWithFixedDelay {
  /** Implementation for `Iterant[Task].intervalWithFixedDelay`. */
  def apply(initialDelay: FiniteDuration, delay: FiniteDuration): Iterant[Task, Long] = {
    // Recursive loop
    def loop(index: Long): Iterant[Task, Long] =
      Iterant.now(index) ++ Task.eval(loop(index + 1)).delayExecution(delay)

    if (initialDelay > Duration.Zero)
      Suspend(Task.eval(loop(0)).delayExecution(initialDelay), Task.unit)
    else
      loop(0)
  }
}
