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

package monix.eval

import java.util.concurrent.{ArrayBlockingQueue, RejectedExecutionException, ThreadPoolExecutor, TimeUnit}

import minitest.SimpleTestSuite
import monix.execution.Scheduler

import scala.concurrent.duration._

object TaskRejectedExecutionSuite extends SimpleTestSuite {
  val limited = Scheduler(
    new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue[Runnable](1))
  )

  def testRejected[A](task: Task[A])(implicit sc: Scheduler): Unit =
    intercept[RejectedExecutionException] {
      Task.gather(List.fill(4)(task)).runSyncUnsafe(3.seconds)
    }

  test("Tasks should propagate RejectedExecutionException") {
    testRejected(Task.pure(0).executeAsync)(limited)
    testRejected(Task.shift)(limited)
    testRejected(Task.pure(0).asyncBoundary(limited))(Scheduler.global)
    testRejected(Task.pure(0).executeOn(limited))(Scheduler.global)
  }
}
