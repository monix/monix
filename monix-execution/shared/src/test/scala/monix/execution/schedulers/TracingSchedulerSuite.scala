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

import java.util.concurrent.TimeUnit

import minitest.SimpleTestSuite
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.misc.Local
import monix.execution.FutureUtils.extensions._
import monix.execution.{Cancelable, Scheduler}
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.exceptions.DummyException

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.Success

object TracingSchedulerSuite extends SimpleTestSuite {
  test("does not capture snapshot if not a tracing scheduler") {
    implicit val ec = TestScheduler()

    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100)(Future(local1.get + local2.get))
    local1 := 999
    local2 := 999

    assertEquals(f.value, None)
    ec.tick()
    assertEquals(f.value, Some(Success(999 * 2)))
  }

  test("captures locals in simulated async execution") {
    val ec = TestScheduler()
    implicit val traced = TracingScheduler(ec)

    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100)(Future(local1.get + local2.get))
    local1 := 999
    local2 := 999

    assertEquals(f.value, None)
    ec.tick()
    assertEquals(f.value, Some(Success(200)))
  }

  testAsync("captures locals in actual async execution") {
    import monix.execution.Scheduler.Implicits.traced
    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100)(Future(local1.get + local2.get))
    local1 := 999
    local2 := 999

    for (r <- f) yield assertEquals(r, 200)
  }

  test("captures locals in scheduleOnce") {
    val ec = TestScheduler()
    implicit val traced = TracingScheduler(ec)

    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100)(Future.delayedResult(1.second)(local1.get + local2.get))
    local1 := 999
    local2 := 999

    assertEquals(f.value, None)
    ec.tick(1.second)
    assertEquals(f.value, Some(Success(200)))
  }

  def testPeriodicScheduling(schedule: (Scheduler, Long, Long, TimeUnit, Runnable) => Cancelable) = {
    val ec = TestScheduler()
    implicit val traced = TracingScheduler(ec)

    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100) {
      var sum = 0
      var count = 0
      val p = Promise[Int]()
      val sub = SingleAssignmentCancelable()

      sub := schedule(traced, 1, 1, TimeUnit.SECONDS, new Runnable {
        def run(): Unit = {
          sum += local1.get + local2.get
          count += 1
          if (count >= 3) {
            p.success(sum)
            sub.cancel()
          }
        }
      })

      p.future
    }

    local1 := 999
    local2 := 999

    assertEquals(f.value, None)
    ec.tick(3.second)
    assertEquals(f.value, Some(Success(200 * 3)))
  }

  test("captures locals in scheduleAtFixedRate") {
    testPeriodicScheduling { (s, initial, delay, unit, r) =>
      s.scheduleAtFixedRate(initial, delay, unit, r)
    }
  }

  test("captures locals in scheduleWithFixedDelay") {
    testPeriodicScheduling { (s, initial, delay, unit, r) =>
      s.scheduleWithFixedDelay(initial, delay, unit, r)
    }
  }

  test("reportFailure") {
    val ec = TestScheduler()
    val traced = TracingScheduler(ec)

    val dummy = DummyException("dummy")
    traced.executeAsync(() => throw dummy)

    ec.tick()
    assertEquals(ec.state.lastReportedError, dummy)
  }

  test("currentTimeMillis") {
    val ec = TestScheduler()
    val traced = TracingScheduler(ec)

    assertEquals(traced.currentTimeMillis(), 0)
    ec.tick(1.second)
    assertEquals(traced.currentTimeMillis(), 1000)
    ec.tick(1.second)
    assertEquals(traced.currentTimeMillis(), 2000)
  }

  test("executionModel") {
    val ec = TestScheduler()
    val traced = TracingScheduler(ec)
    assertEquals(traced.executionModel, ec.executionModel)

    implicit val traced2 = traced.withExecutionModel(AlwaysAsyncExecution)
    assertEquals(traced2.executionModel, AlwaysAsyncExecution)

    val f = Future(true)
    assertEquals(f.value, None)
    ec.tick()
    assertEquals(f.value, Some(Success(true)))
  }
}

