/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import monix.execution.{ ExecutionModel, Scheduler }
import monix.execution.schedulers.SchedulerService
import monix.execution.misc.Local
import scala.concurrent.Await
import scala.concurrent.duration._

/** [[https://github.com/monix/monix/pull/993]]
  */
class TaskIssue993Suite extends BaseTestSuite {
  def loop[A, S](self: Task[A], initial: S)(f: (A, S, S => Task[S]) => Task[S]): Task[S] =
    self.flatMap { a =>
      f(a, initial, loop(self, _)(f))
    }

  test("should not throw NullPointerException (issue #993)") {
    import monix.execution.misc.CanBindLocals.Implicits.synchronousAsDefault
    implicit val sc: SchedulerService =
      Scheduler
        .computation(parallelism = 4)
        .withExecutionModel(ExecutionModel.BatchedExecution(128))

    try {
      val loopWithAsyncBoundaries = loop(Task(1), 0) { (a, sum, continue) =>
        val n = sum + a
        if (n < 100000) continue(n) else Task.now(n)
      }

      val local = Local(0)
      val task = for {
        r0 <- loopWithAsyncBoundaries
        r1 <- Task(Local.isolate {
          local.bind(100)(local.get)
        })
        _ <- Task.shift
        r2 <- Task(Local.isolate {
          local.bind(200)(local.get)
        })
      } yield {
        r0 + r1 + r2
      }

      val f = task.runToFuture
      val r = Await.result(f, 3.minutes)
      assertEquals(r, 100000 + 100 + 200)
    } finally {
      sc.shutdown()
    }
  }
}
