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

package monix.reactive
package issues

import minitest.TestSuite
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.subjects.{AsyncSubject, Subject}
import scala.concurrent.Await
import scala.concurrent.duration._

object Issue908Suite extends TestSuite[SchedulerService] {
  val CONCURRENT_TASKS = 10000
  val CYCLES = 100

  def setup(): SchedulerService = {
    Scheduler.computation(
      parallelism = math.max(Runtime.getRuntime.availableProcessors(), 2),
      name = "issue908-suite",
      daemonic = true)
  }

  def tearDown(env: SchedulerService): Unit = {
    import monix.execution.Scheduler.Implicits.global
    env.shutdown()
    assert(env.awaitTermination(1.minute), "scheduler.awaitTermination")
  }

  test("concurrent test (1)") { implicit sc =>
    for (_ <- 0 until CYCLES) {
      val subject = AsyncSubject.apply[Int]()

      val tasks = (0 until CONCURRENT_TASKS).map { _ =>
        subject.firstL.timeoutTo(1.millis, Task(1))
      }

      val await = Task.gatherUnordered(tasks).map(_.sum)
      val f = Await.result(await.runToFuture, 30.seconds)

      assertEquals(f, CONCURRENT_TASKS)
      print(".")
    }
    println()
  }

  test("concurrent test (2)") { implicit sc =>
    for (_ <- 0 until 1000) {
      val subject = AsyncSubject.apply[Int]()

      val tasks = (0 until CONCURRENT_TASKS).map { _ =>
        subject.firstL.timeoutTo(30.seconds, Task(1))
      }

      val await = for {
        fiber <- Task.gatherUnordered(tasks).map(_.sum).start
        _ <- awaitSubscribers(subject, CONCURRENT_TASKS)
        _ <- Task {
          subject.onNext(2)
          subject.onComplete()
        }
        result <- fiber.join
      } yield {
        result
      }

      val f = Await.result(await.runToFuture, 30.seconds)
      assertEquals(f, CONCURRENT_TASKS * 2)
      print(".")
    }
    println()
  }

  def awaitSubscribers(subject: Subject[_, _], nr: Int): Task[Unit] =
    Task.suspend {
      if (subject.size < nr)
        Task.sleep(1.millis).flatMap(_ => awaitSubscribers(subject, nr))
      else
        Task.unit
    }
}
