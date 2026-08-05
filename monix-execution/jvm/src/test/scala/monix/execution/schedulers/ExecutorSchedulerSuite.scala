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

import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit, TimeoutException }
import minitest.TestSuite
import monix.execution.ExecutionModel.{ AlwaysAsyncExecution, Default as DefaultExecutionModel }
import monix.execution.atomic.Atomic
import monix.execution.cancelables.SingleAssignCancelable
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.ExecutorSchedulerSuite.{ TaskRunPeriod, TestException, TestFatalError }
import monix.execution.{ Cancelable, Scheduler, UncaughtExceptionReporter }

import scala.concurrent.duration.*
import scala.concurrent.{ blocking, Await, Promise }

abstract class ExecutorSchedulerSuite extends TestSuite[SchedulerService] { self =>
  private val recordingReporter = Atomic(new RecordingReporter)
  private val unexpectedFailure = Atomic(null: Throwable)

  protected val testsReporter: UncaughtExceptionReporter =
    UncaughtExceptionReporter { failure =>
      if ((failure ne TestException) && (failure ne TestFatalError)) unexpectedFailure.set(failure)
      recordingReporter.get().reportFailure(failure)
    }

  protected val unexpectedTestFailureReporter: UncaughtExceptionReporter =
    UncaughtExceptionReporter { failure => unexpectedFailure.set(failure) }

  def createScheduler(): SchedulerService

  def setup(): SchedulerService = {
    recordingReporter.set(new RecordingReporter)
    unexpectedFailure.set(null)
    createScheduler()
  }

  override def tearDown(scheduler: SchedulerService): Unit = {
    try assert(!scheduler.isShutdown)
    finally scheduler.shutdown()
    assert(scheduler.isShutdown, "scheduler.isShutdown")
    val result = scheduler.awaitTermination(10.seconds)
    assert(result, "scheduler.awaitTermination")
    assert(scheduler.isTerminated, "scheduler.isTerminated")

    val unexpected = unexpectedFailure.get()
    assert(unexpected == null, s"unexpected failure reported: $unexpected")
  }

  def scheduleOnce(s: Scheduler, delay: FiniteDuration)(action: => Unit): Cancelable =
    s.scheduleOnce(delay.length, delay.unit, runnableAction(action))

  test("scheduleOnce with delay") { scheduler =>
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    val _ = scheduleOnce(scheduler, 100.millis) {
      p.success(System.nanoTime())
      ()
    }
    val timeTaken = Await.result(p.future, 3.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("scheduleOnce with delay lower than 1.milli") { scheduler =>
    val p = Promise[Int]()
    val _ = scheduleOnce(scheduler, 20.nanos) { p.success(1); () }
    assert(Await.result(p.future, 3.seconds) == 1)
  }

  test("scheduleOnce with negative delay") { scheduler =>
    val p = Promise[Int]()
    val _ = scheduler.scheduleOnce(-100.millis) { p.success(1); () }
    assertEquals(Await.result(p.future, 3.seconds), 1)
  }

  test("scheduleOnce with delay and cancel") { scheduler =>
    val p = Promise[Int]()
    val task = scheduleOnce(scheduler, 100.millis) { p.success(1); () }
    task.cancel()

    val _ = intercept[TimeoutException] {
      val _ = Await.result(p.future, 150.millis)
      ()
    }
    ()
  }

  test("schedule with fixed delay") { scheduler =>
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

  test("schedule at fixed rate") { scheduler =>
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

  test("execute local") { scheduler =>
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

  test("change execution model") { scheduler =>
    val s: Scheduler = scheduler
    assertEquals(s.executionModel, DefaultExecutionModel)
    val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    assertEquals(s.executionModel, DefaultExecutionModel)
    assertEquals(s2.executionModel, AlwaysAsyncExecution)
  }

  test("scheduleAtFixedRate stops after a failure") { scheduler =>
    assertStopsAfterFailure(
      scheduleFailure = failure => scheduler.scheduleAtFixedRate(0.seconds, TaskRunPeriod)(failure.run())
    )
  }

  test("scheduleAtFixedRate stops after a fatal error") { scheduler =>
    assertStopsAfterFailure(
      scheduleFailure = failure => scheduler.scheduleAtFixedRate(0.seconds, TaskRunPeriod)(failure.run()),
      failWith = TestFatalError
    )
  }

  test("scheduleWithFixedDelay stops after a failure") { scheduler =>
    assertStopsAfterFailure(
      scheduleFailure = failure => scheduler.scheduleWithFixedDelay(0.seconds, TaskRunPeriod)(failure.run())
    )
  }

  test("scheduleWithFixedDelay stops after a fatal error") { scheduler =>
    assertStopsAfterFailure(
      scheduleFailure = failure => scheduler.scheduleWithFixedDelay(0.seconds, TaskRunPeriod)(failure.run()),
      failWith = TestFatalError
    )
  }

  test("reports fatal errors on scheduleOnce") { scheduler =>
    assertScheduledFailureReported(
      scheduleFailure = failure => scheduler.scheduleOnce(1.milli)(throw failure),
      failure = TestFatalError
    )
  }

  test("reports fatal errors on scheduleAtFixedRate") { scheduler =>
    assertScheduledFailureReported(
      scheduleFailure = failure => scheduler.scheduleAtFixedRate(0.seconds, TaskRunPeriod)(throw failure),
      failure = TestFatalError
    )
  }

  test("reports fatal errors on scheduleWithFixedDelay") { scheduler =>
    assertScheduledFailureReported(
      scheduleFailure = failure => scheduler.scheduleWithFixedDelay(0.seconds, TaskRunPeriod)(throw failure),
      failure = TestFatalError
    )
  }

  test("does not report the interruption of cancelling a running scheduleOnce") { scheduler =>
    val started = new CountDownLatch(1)
    val blocked = new CountDownLatch(1)

    val schedule = scheduler.scheduleOnce(1.milli) {
      started.countDown()
      // Parks at an interruptible point, so that cancelling the schedule throws here
      val _ = blocked.await(15, TimeUnit.MINUTES)
    }

    try {
      assert(started.await(15, TimeUnit.SECONDS), "The task was never executed")
      schedule.cancel()

      assertNothingReported()
    } finally {
      // Releases the task on the schedulers where cancelling does not interrupt it
      blocked.countDown()
    }
  }

  /** Tests the documented contract (stopping after failure) of
   * [[monix.execution.Scheduler.scheduleAtFixedRate(initialDelay:Long* Scheduler.scheduleAtFixedRate]], and
   * [[monix.execution.Scheduler.scheduleWithFixedDelay(initialDelay:Long* Scheduler.scheduleWithFixedDelay]].
   */
  private def assertStopsAfterFailure(
    scheduleFailure: Runnable => Cancelable,
    failWith: Throwable = TestException
  ): Unit = {
    val executed = new CountDownLatch(1)
    val executedTwice = new CountDownLatch(2)
    val schedule = scheduleFailure { () =>
      executed.countDown()
      executedTwice.countDown()
      throw failWith
    }

    try {
      // Getting scheduled at all can take a while on a loaded machine, hence the generous wait here.
      assert(executed.await(15, TimeUnit.SECONDS), "The task was never executed")
      assert(
        !executedTwice.await(TaskRunPeriod.toMillis * 5, TimeUnit.MILLISECONDS),
        "The task was executed again after it failed"
      )
    } finally schedule.cancel()
  }

  private def assertScheduledFailureReported(scheduleFailure: Throwable => Cancelable, failure: Throwable): Unit = {
    val schedule = scheduleFailure(failure)
    try assertReportedOnce(failure)
    finally schedule.cancel()
  }

  protected def assertReportFailureReachesTheReporter(scheduler: Scheduler): Unit = {
    scheduler.reportFailure(TestException)
    assertReportedOnce(TestException)
  }

  private def assertReportedOnce(expected: Throwable): Unit = {
    val reporter = recordingReporter.get()
    assertEquals(Await.result(reporter.firstFailure, 15.seconds), expected)
    intercept[TimeoutException] {
      Await.result(reporter.secondFailure, TaskRunPeriod * 5)
    }
  }

  private def assertNothingReported(): Unit = {
    val reporter = recordingReporter.get()
    intercept[TimeoutException] {
      Await.result(reporter.firstFailure, TaskRunPeriod * 5)
    }
  }

  def runnableAction(f: => Unit): Runnable =
    () => f
}

object ExecutorSchedulerSuite {
  private val TestException = DummyException("dummy")
  private val TestFatalError = new LinkageError("dummy-fatal")

  private val TaskRunPeriod = 10.millis
}

object ComputationSchedulerSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler.computation(name = "monix-tests-computation", parallelism = 4, reporter = testsReporter)

  test("reportFailure hands the failure to the reporter") { scheduler =>
    assertReportFailureReachesTheReporter(scheduler)
  }
}

object ForkJoinSchedulerSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler
      .forkJoin(name = "monix-tests-forkjoin", parallelism = 4, maxThreads = 256, reporter = testsReporter)

  test("reportFailure hands the failure to the reporter") { scheduler =>
    assertReportFailureReachesTheReporter(scheduler)
  }

  test("integrates with Scala's BlockContext") { scheduler =>
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

object FixedPoolSchedulerSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler.fixedPool("monix-tests-fixedPool", poolSize = 4, reporter = testsReporter)
}

object SingleThreadSchedulerSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler.singleThread("monix-tests-singleThread", reporter = testsReporter)
}

object CachedSchedulerSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler.cached("monix-tests-cached", 1, 4, reporter = testsReporter)

  test("reportFailure hands the failure to the reporter") { scheduler =>
    assertReportFailureReachesTheReporter(scheduler)
  }
}

object IOSchedulerSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler.io("monix-tests-io", reporter = testsReporter)

  test("reportFailure hands the failure to the reporter") { scheduler =>
    assertReportFailureReachesTheReporter(scheduler)
  }
}

object SingleThreadScheduledExecutorSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler(
      executor = Executors.newSingleThreadScheduledExecutor(threadFactoryBuilder),
      reporter = testsReporter
    )

  private def threadFactoryBuilder = ThreadFactoryBuilder(
    name = "monix-tests-single-thread-scheduled",
    reporter = unexpectedTestFailureReporter,
    daemonic = true,
  )

  test("reportFailure hands the failure to the reporter") { scheduler =>
    assertReportFailureReachesTheReporter(scheduler)
  }
}

object ScheduledExecutorSuite extends ExecutorSchedulerSuite {
  def createScheduler(): SchedulerService =
    monix.execution.Scheduler(
      executor = Executors.newScheduledThreadPool(4, threadFactoryBuilder),
      reporter = testsReporter
    )

  private def threadFactoryBuilder = ThreadFactoryBuilder(
    name = "monix-tests-scheduled",
    reporter = unexpectedTestFailureReporter,
    daemonic = true,
  )

  test("reportFailure hands the failure to the reporter") { scheduler =>
    assertReportFailureReachesTheReporter(scheduler)
  }
}
