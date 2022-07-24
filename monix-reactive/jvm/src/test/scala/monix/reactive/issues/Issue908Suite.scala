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

package monix.reactive
package issues

import monix.execution.{ BaseTestSuite, Scheduler, TestSuite, UncaughtExceptionReporter }
import monix.eval.Task
import monix.execution.schedulers.SchedulerService
import monix.reactive.subjects.{ AsyncSubject, Subject }

import scala.concurrent.{ Await, TimeoutException }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class Issue908Suite extends TestSuite[SchedulerService] {
  val CONCURRENT_TASKS = 1000
  val CYCLES = 100

  def setup(): SchedulerService = {
    Scheduler.computation(
      parallelism = math.max(Runtime.getRuntime.availableProcessors(), 2),
      name = "issue908-suite",
      daemonic = true,
      reporter = UncaughtExceptionReporter(_ => ())
    )
  }

  def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    assert(env.awaitTermination(1.minute), "scheduler.awaitTermination")
  }

  fixture.test("broken tasks test (1)") { implicit sc =>
    for (_ <- 0 until CYCLES) {
      val task = Task.async[String] { cb =>
        sc.execute(() => cb.onSuccess("1"))
        sc.execute(() => cb.onSuccess("2"))
      }

      val f = Task.race(task, task).runToFuture
      val r = Await.result(f, 30.seconds)

      assert(r != null, "r != null")
      assert(r.isInstanceOf[Either[_, _]], "r.isInstanceOf[Either[_, _]]")
      val i = r.fold(x => x, x => x)
      assert(i == "1" || i == "2", s"$i == 1 || $i == 2")
    }
  }

  fixture.test("broken tasks test (2)") { implicit sc =>
    for (_ <- 0 until CYCLES) {
      val task = Task.async[String] { cb =>
        sc.execute(() => cb.onSuccess("1"))
        sc.execute(() => cb.onSuccess("2"))
      }

      val f = Task.raceMany((0 until CONCURRENT_TASKS).map(_ => task)).runToFuture
      val r = Await.result(f, 30.seconds)

      assert(r == "1" || r == "2", s"$r == 1 || $r == 2")
    }
  }

  fixture.test("broken tasks test (3)") { implicit sc =>
    for (_ <- 0 until CYCLES) {
      val task = Task.async[String] { cb =>
        sc.execute(() => cb.onSuccess("1"))
        sc.execute(() => cb.onSuccess("2"))
      }

      val f = task.timeout(1.millis).materialize.runToFuture
      Await.result(f, 30.seconds) match {
        case Success("1" | "2") =>
        case Failure(_: TimeoutException) =>
        case other =>
          fail(s"Invalid value: $other")
      }
    }
  }

  fixture.test("concurrent test (1)") { implicit sc =>
    for (_ <- 0 until CYCLES) {
      val subject = AsyncSubject.apply[Int]()

      val tasks = (0 until CONCURRENT_TASKS).map { _ =>
        subject.firstL.timeoutTo(1.millis, Task(1))
      }

      val await = Task.parSequenceUnordered(tasks).map(_.sum)
      val f = Await.result(await.runToFuture, 30.seconds)

      assertEquals(f, CONCURRENT_TASKS)
    }
  }

  fixture.test("concurrent test (2)") { implicit sc =>
    for (_ <- 0 until CYCLES) {
      val subject = AsyncSubject.apply[Int]()

      val tasks = (0 until CONCURRENT_TASKS).map { _ =>
        subject.firstL.timeoutTo(30.seconds, Task(1))
      }

      val await = for {
        fiber <- Task.parSequenceUnordered(tasks).map(_.sum).start
        _     <- awaitSubscribers(subject, CONCURRENT_TASKS)
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
    }
  }

  def awaitSubscribers(subject: Subject[_, _], nr: Int): Task[Unit] =
    Task.suspend {
      if (subject.size < nr)
        Task.sleep(1.millis).flatMap(_ => awaitSubscribers(subject, nr))
      else
        Task.unit
    }
}
