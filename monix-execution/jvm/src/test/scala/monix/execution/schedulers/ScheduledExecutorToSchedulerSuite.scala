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

import java.util.concurrent._
import minitest.TestSuite
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{Scheduler, UncaughtExceptionReporter, ExecutionModel => ExecModel}
import monix.execution.atomic.Atomic
import monix.execution.cancelables.SingleAssignmentCancelable
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

object ScheduledExecutorToSchedulerSuite extends TestSuite[ExecutorScheduler] {
  val lastError = Atomic(null : Throwable)
  def setup(): ExecutorScheduler = {
    val reporter = UncaughtExceptionReporter(lastError.set)
    val executor = Executors.newScheduledThreadPool(2,
      ThreadFactoryBuilder(
        "ExecutorSchedulerSuite",
        reporter,
        daemonic = true
      ))

    ExecutorScheduler(executor,
      reporter,
      ExecModel.Default)
  }

  override def tearDown(scheduler: ExecutorScheduler): Unit = {
    scheduler.shutdown()
    val ex = lastError.getAndSet(null)
    if (ex != null) throw ex
    val result = Await.result(scheduler.awaitTermination(10.seconds, Scheduler.global), 30.seconds)
    assert(result, "scheduler.awaitTermination")
  }

  test("scheduleOnce with delay") { implicit s =>
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    s.scheduleOnce(100.millis)(p.success(System.nanoTime()))

    val timeTaken = Await.result(p.future, 30.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("scheduleOnce with negative delay") { implicit s =>
    val p = Promise[Boolean]()
    s.scheduleOnce(-100.millis)(p.success(true))

    val result = Await.result(p.future, 30.second)
    assert(result)
  }

  test("reportFailure") { implicit s =>
    val dummy = new RuntimeException("dummy, please ignore")
    s.reportFailure(dummy)
    assertEquals(lastError.getAndSet(null), dummy)
  }

  test("scheduleOnce with delay lower than 1.milli") { implicit s =>
    val p = Promise[Int]()
    s.scheduleOnce(20.nanos)(p.success(1))
    assert(Await.result(p.future, 3.seconds) == 1)
  }

  test("scheduleOnce with delay and cancel") { implicit s =>
    val p = Promise[Int]()
    val task = s.scheduleOnce(100.millis)(p.success(1))
    task.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule with fixed delay") { implicit s =>
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub := s.scheduleWithFixedDelay(10.millis, 50.millis) {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    }

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("schedule at fixed rate") { implicit s =>
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub := s.scheduleAtFixedRate(10.millis, 50.millis) {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    }

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("execute local") { implicit s =>
    var result = 0
    def loop(n: Int): Unit =
      s.executeTrampolined { () =>
        result += 1
        if (n-1 > 0) loop(n-1)
      }

    val count = 100000
    loop(count)
    assertEquals(result, count)
  }

  test("change execution model") { implicit s =>
    assertEquals(s.executionModel, ExecModel.Default)
    val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    assertEquals(s.executionModel, ExecModel.Default)
    assertEquals(s2.executionModel, AlwaysAsyncExecution)
  }
}