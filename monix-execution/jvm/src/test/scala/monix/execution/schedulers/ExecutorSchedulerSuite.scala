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

package monix.execution.schedulers

import java.util.concurrent.{ CountDownLatch, TimeUnit, TimeoutException }

import monix.execution.TestSuite
import monix.execution.ExecutionModel.{ AlwaysAsyncExecution, Default => DefaultExecutionModel }
import monix.execution.cancelables.SingleAssignCancelable
import monix.execution.exceptions.DummyException
import monix.execution.{ Cancelable, Scheduler, UncaughtExceptionReporter }

import scala.concurrent.duration._
import scala.concurrent.{ blocking, Await, Promise }
class TestReporter(
  var lastReportedFailure: Throwable = null,
  var lastReportedFailureLatch: CountDownLatch = null
) {
  val reporter = UncaughtExceptionReporter { ex =>
    lastReportedFailure = ex
    if (lastReportedFailureLatch != null)
      lastReportedFailureLatch.countDown()
    else
      ex.printStackTrace()
  }
}
abstract class ExecutorSchedulerSuite extends TestSuite[(TestReporter, SchedulerService)] { self =>

  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService

  override def setup(): (TestReporter, SchedulerService) = {
    val testsReporter = new TestReporter()
    (testsReporter, scheduler(testsReporter.reporter))
  }

  override def tearDown(schedulerWithReporter: (TestReporter, SchedulerService)): Unit = {
    val scheduler = schedulerWithReporter._2
    try assert(!scheduler.isShutdown)
    finally scheduler.shutdown()
    assert(scheduler.isShutdown, "scheduler.isShutdown")
    val result = scheduler.awaitTermination(10.seconds)
    assert(result, "scheduler.awaitTermination")
    assert(scheduler.isTerminated, "scheduler.isTerminated")
  }

  def scheduleOnce(s: Scheduler, delay: FiniteDuration)(action: => Unit): Cancelable =
    s.scheduleOnce(delay.length, delay.unit, runnableAction(action))

  fixture.test("scheduleOnce with delay") {
    case (_, scheduler) =>
      val p = Promise[Long]()
      val startedAt = System.nanoTime()
      scheduleOnce(scheduler, 100.millis) {
        p.success(System.nanoTime())
        ()
      }
      val timeTaken = Await.result(p.future, 3.second)
      assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  fixture.test("scheduleOnce with delay lower than 1.milli") {
    case (_, scheduler) =>
      val p = Promise[Int]()
      scheduleOnce(scheduler, 20.nanos) { p.success(1); () }
      assert(Await.result(p.future, 3.seconds) == 1)
  }

  fixture.test("scheduleOnce with delay and cancel") {
    case (_, scheduler) =>
      val p = Promise[Int]()
      val task = scheduleOnce(scheduler, 100.millis) { p.success(1); () }
      task.cancel()

      intercept[TimeoutException] {
        Await.result(p.future, 150.millis)
        ()
      }
      ()
  }

  fixture.test("schedule with fixed delay") {
    case (_, scheduler) =>
      val sub = SingleAssignCancelable()
      val p = Promise[Int]()
      var value = 0

      sub := scheduler.scheduleWithFixedDelay(
        10,
        50,
        TimeUnit.MILLISECONDS,
        runnableAction {
          if (value + 1 == 4) {
            value += 1
            sub.cancel()
            p.success(value)
            ()
          } else if (value < 4) {
            value += 1
          }
        }
      )

      assert(Await.result(p.future, 5.second) == 4)
  }

  fixture.test("schedule at fixed rate") {
    case (_, scheduler) =>
      val sub = SingleAssignCancelable()
      val p = Promise[Int]()
      var value = 0

      sub := scheduler.scheduleAtFixedRate(
        10,
        50,
        TimeUnit.MILLISECONDS,
        runnableAction {
          if (value + 1 == 4) {
            value += 1
            sub.cancel()
            p.success(value)
            ()
          } else if (value < 4) {
            value += 1
          }
        }
      )

      assert(Await.result(p.future, 5.second) == 4)
  }

  fixture.test("execute local") {
    case (_, scheduler) =>
      var result = 0
      def loop(n: Int): Unit =
        scheduler.executeTrampolined { () =>
          result += 1
          if (n - 1 > 0) loop(n - 1)
        }

      val count = 100000
      loop(count)
      assertEquals(result, count)
  }

  fixture.test("change execution model") {
    case (_, scheduler) =>
      val s: Scheduler = scheduler
      assertEquals(s.executionModel, DefaultExecutionModel)
      val s2 = s.withExecutionModel(AlwaysAsyncExecution)
      assertEquals(s.executionModel, DefaultExecutionModel)
      assertEquals(s2.executionModel, AlwaysAsyncExecution)
  }

  fixture.test("reports errors on execute") {
    case (reporter, scheduler) =>
      val latch = new CountDownLatch(1)
      reporter.lastReportedFailureLatch = latch

      val ex = DummyException("dummy")
      scheduler.execute(() => throw ex)

      assert(latch.await(15, TimeUnit.MINUTES), "lastReportedFailureLatch.await")
      self.synchronized(assertEquals(reporter.lastReportedFailure, ex))
  }

  fixture.test("reports errors on scheduleOnce") {
    case (reporter, scheduler) =>
      val latch = new CountDownLatch(1)
      reporter.lastReportedFailureLatch = latch

      val ex = DummyException("dummy")

      scheduler.scheduleOnce(1, TimeUnit.MILLISECONDS, () => throw ex)

      assert(latch.await(15, TimeUnit.MINUTES), "lastReportedFailureLatch.await")
      self.synchronized(assertEquals(reporter.lastReportedFailure, ex))

  }

  def runnableAction(f: => Unit): Runnable =
    () => f
}

class ComputationSchedulerSuite extends ExecutorSchedulerSuite {
  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService =
    monix.execution.Scheduler
      .forkJoin(name = "monix-tests-computation", parallelism = 4, maxThreads = 256, reporter = reporter)
}

class ForkJoinSchedulerSuite extends ExecutorSchedulerSuite {
  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService =
    monix.execution.Scheduler
      .forkJoin(name = "monix-tests-forkjoin", parallelism = 4, maxThreads = 256, reporter = reporter)

  fixture.test("integrates with Scala's BlockContext") {
    case (_, scheduler) =>
      val threadsCount = 100
      val latch = new CountDownLatch(100)
      val finish = new CountDownLatch(1)

      for (_ <- 0 until threadsCount)
        scheduler.execute { () =>
          blocking {
            latch.countDown()
            finish.await(15, TimeUnit.MINUTES)
            ()
          }
        }

      assert(latch.await(15, TimeUnit.MINUTES), "latch.await")
      finish.countDown()
  }
}

class FixedPoolSchedulerSuite extends ExecutorSchedulerSuite {
  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService =
    monix.execution.Scheduler.fixedPool("monix-tests-fixedPool", poolSize = 4, reporter = reporter)
}

class SingleThreadSchedulerSuite extends ExecutorSchedulerSuite {
  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService =
    monix.execution.Scheduler.singleThread("monix-tests-singleThread", reporter = reporter)
}

class CachedSchedulerSuite extends ExecutorSchedulerSuite {
  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService =
    monix.execution.Scheduler.cached("monix-tests-cached", 1, 4, reporter = reporter)
}

class IOSchedulerSuite extends ExecutorSchedulerSuite {
  def scheduler(reporter: UncaughtExceptionReporter): SchedulerService =
    monix.execution.Scheduler.io("monix-tests-io", reporter = reporter)
}
