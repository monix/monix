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

package monix.execution.schedulers

import java.util.concurrent.{CountDownLatch, TimeUnit, TimeoutException}

import minitest.TestSuite
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, Default => DefaultExecutionModel}
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.exceptions.DummyException
import monix.execution.{Cancelable, Scheduler, UncaughtExceptionReporter}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise, blocking}

abstract class ExecutorSchedulerSuite extends TestSuite[SchedulerService] { self =>
  var lastReportedFailure = null : Throwable
  var lastReportedFailureLatch = null : CountDownLatch

  val testsReporter = UncaughtExceptionReporter { ex =>
    self.synchronized {
      lastReportedFailure = ex
      if (lastReportedFailureLatch != null)
        lastReportedFailureLatch.countDown()
      else
        ex.printStackTrace()
    }
  }

  override def tearDown(scheduler: SchedulerService): Unit = {
    try assert(!scheduler.isShutdown) finally scheduler.shutdown()
    assert(scheduler.isShutdown, "scheduler.isShutdown")
    val result = Await.result(scheduler.awaitTermination(10.seconds, Scheduler.global), 30.seconds)
    assert(result, "scheduler.awaitTermination")
    assert(scheduler.isTerminated, "scheduler.isTerminated")
  }

  def scheduleOnce(s: Scheduler, delay: FiniteDuration)(action: => Unit): Cancelable =
    s.scheduleOnce(delay.length, delay.unit, runnableAction(action))

  test("scheduleOnce with delay") { scheduler =>
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    scheduleOnce(scheduler, 100.millis)(p.success(System.nanoTime()))

    val timeTaken = Await.result(p.future, 3.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("scheduleOnce with delay lower than 1.milli") { scheduler =>
    val p = Promise[Int]()
    scheduleOnce(scheduler, 20.nanos)(p.success(1))
    assert(Await.result(p.future, 3.seconds) == 1)
  }

  test("scheduleOnce with delay and cancel") { scheduler =>
    val p = Promise[Int]()
    val task = scheduleOnce(scheduler, 100.millis)(p.success(1))
    task.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule with fixed delay") { scheduler =>
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub := scheduler.scheduleWithFixedDelay(10, 50, TimeUnit.MILLISECONDS, runnableAction {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("schedule at fixed rate") { scheduler =>
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub := scheduler.scheduleAtFixedRate(10, 50, TimeUnit.MILLISECONDS, runnableAction {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("execute local") { scheduler =>
    var result = 0
    def loop(n: Int): Unit =
      scheduler.executeTrampolined { () =>
        result += 1
        if (n-1 > 0) loop(n-1)
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

  test("reports errors on execute") { scheduler =>
    val latch = new CountDownLatch(1)
    self.synchronized {
      lastReportedFailure = null
      lastReportedFailureLatch = latch
    }

    try {
      val ex = DummyException("dummy")

      scheduler.execute(new Runnable {
        override def run() =
          throw ex
      })

      assert(latch.await(15, TimeUnit.MINUTES), "lastReportedFailureLatch.await")
      self.synchronized(assertEquals(lastReportedFailure, ex))
    } finally {
      self.synchronized {
        lastReportedFailure = null
        lastReportedFailureLatch = null
      }
    }
  }

  test("reports errors on scheduleOnce") { scheduler =>
    val latch = new CountDownLatch(1)
    self.synchronized {
      lastReportedFailure = null
      lastReportedFailureLatch = latch
    }

    try {
      val ex = DummyException("dummy")

      scheduler.scheduleOnce(1, TimeUnit.MILLISECONDS, new Runnable {
        override def run() =
          throw ex
      })

      assert(latch.await(15, TimeUnit.MINUTES), "lastReportedFailureLatch.await")
      self.synchronized(assertEquals(lastReportedFailure, ex))
    } finally {
      self.synchronized {
        lastReportedFailure = null
        lastReportedFailureLatch = null
      }
    }
  }

  def runnableAction(f: => Unit): Runnable =
    new Runnable { def run() = f }
}

object ComputationSchedulerSuite extends ExecutorSchedulerSuite {
  def setup(): SchedulerService =
    monix.execution.Scheduler.forkJoin(
      name = "monix-tests-computation",
      parallelism = 4,
      maxThreads = 256,
      reporter=testsReporter)
}

object ForkJoinSchedulerSuite extends ExecutorSchedulerSuite {
  def setup(): SchedulerService =
    monix.execution.Scheduler.forkJoin(
      name = "monix-tests-forkjoin",
      parallelism = 4,
      maxThreads = 256,
      reporter=testsReporter)

  test("integrates with Scala's BlockContext") { scheduler =>
    val threadsCount = 100
    val latch = new CountDownLatch(100)
    val finish = new CountDownLatch(1)

    for (_ <- 0 until threadsCount)
      scheduler.executeAsync { () =>
        blocking {
          latch.countDown()
          finish.await(15, TimeUnit.MINUTES)
        }
      }

    assert(latch.await(15, TimeUnit.MINUTES), "latch.await")
    finish.countDown()
  }
}

object FixedPoolSchedulerSuite extends ExecutorSchedulerSuite {
  def setup(): SchedulerService =
    monix.execution.Scheduler.fixedPool("monix-tests-fixedPool", poolSize = 4, reporter=testsReporter)
}

object SingleThreadSchedulerSuite extends ExecutorSchedulerSuite {
  def setup(): SchedulerService =
    monix.execution.Scheduler.singleThread("monix-tests-singleThread", reporter=testsReporter)
}

object CachedSchedulerSuite extends ExecutorSchedulerSuite {
  def setup(): SchedulerService =
    monix.execution.Scheduler.cached("monix-tests-cached", 1, 4, reporter=testsReporter)
}

object IOSchedulerSuite extends ExecutorSchedulerSuite {
  def setup(): SchedulerService =
    monix.execution.Scheduler.io("monix-tests-io", reporter=testsReporter)
}