/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import java.util.concurrent.{TimeUnit, TimeoutException}

import minitest.TestSuite
import monix.execution.Scheduler
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

object TestSchedulerSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty)
  }

  test("should execute asynchronously") { s =>
    var wasExecuted = false
    s.executeAsync { () =>
      wasExecuted = true
    }
    assert(!wasExecuted)

    s.tick()
    assert(wasExecuted)
  }

  test("should execute the whole stack") { s =>
    var iterations = 0

    s.executeAsync { () =>
      s.executeAsync { () =>
        iterations += 1
        s.executeAsync { () =>
          iterations += 1
        }
      }

      assert(iterations == 0)
      iterations += 1

      s.executeAsync { () =>
        iterations += 1
        s.executeAsync { () =>
          iterations += 1
        }
      }
    }

    assert(iterations == 0)
    s.tick()
    assert(iterations == 5)
  }

  test("should schedule stuff in the future") { s =>
    var firstBatch = 0
    var secondBatch = 0

    s.scheduleOnce(10, TimeUnit.SECONDS, action {
      firstBatch += 1
      s.execute(action { firstBatch += 1 })
      s.scheduleOnce(10, TimeUnit.SECONDS, action {
        firstBatch += 1
      })
      ()
    })

    s.scheduleOnce(20, TimeUnit.SECONDS, action {
      secondBatch += 1
      s.execute(action { secondBatch += 1 })
      s.scheduleOnce(10, TimeUnit.SECONDS, action {
        secondBatch += 1
      })
      ()
    })

    s.tick()
    assert(firstBatch == 0 && secondBatch == 0)

    s.tick(9.seconds)
    assert(firstBatch == 0 && secondBatch == 0)

    s.tick(1.second)
    assert(firstBatch == 2 && secondBatch == 0)

    s.tick(10.seconds)
    assert(firstBatch == 3 && secondBatch == 2)

    s.tick(10.seconds)
    assert(firstBatch == 3 && secondBatch == 3)
  }

  test("should work correctly for ticks spanning several tasks, test 1") { implicit s =>
    val f = delayedResult(50.millis, 300.millis)("hello world")
    assertEquals(f.value, None)

    s.tick(10.seconds)
    assertEquals(f.value, Some(Success("hello world")))
  }

  test("should work correctly for ticks spanning several tasks, test 2") { implicit s =>
    val f = delayedResult(500.millis, 300.millis)("hello world")
    assertEquals(f.value, None)

    s.tick(10.seconds)
    intercept[TimeoutException] { f.value.get.get; () }
    ()
  }

  test("should work correctly for ticks spanning several tasks, test 3") { implicit s =>
    val f = delayedResult(50.millis, 300.millis)("hello world")
    assertEquals(f.value, None)

    s.tick(50.seconds)
    s.tick(300.millis)

    assertEquals(f.value, Some(Success("hello world")))
  }

  test("should work correctly for ticks spanning several tasks, test 4") { implicit s =>
    val f = delayedResult(50.millis, 300.millis)("hello world")
    assertEquals(f.value, None)

    s.tick(40.seconds)
    s.tick(10.seconds)
    s.tick(300.millis)

    assertEquals(f.value, Some(Success("hello world")))
  }

  test("complicated scheduling, test 1") { implicit s =>
    var counter = 0

    delayedResult(50.millis, 300.millis) {
      counter += 1

      delayedResult(50.millis, 300.millis) {
        counter += 1

        delayedResult(50.millis, 300.millis) {
          counter += 1

          delayedResult(50.millis, 300.millis) {
            counter += 1

            delayedResult(50.millis, 300.millis) {
              counter += 1
            }
          }
        }
      }
    }

    s.tick(250.millis)

    assert(counter == 5)
    assert(s.state.tasks.isEmpty)
  }

  test("complicated scheduling, test 2") { implicit s =>
    var counter = 0

    delayedResult(50.millis, 300.millis) {
      counter += 1

      delayedResult(50.millis, 300.millis) {
        counter += 1

        delayedResult(50.millis, 300.millis) {
          counter += 1

          delayedResult(50.millis, 300.millis) {
            counter += 1

            delayedResult(50.millis, 300.millis) {
              counter += 1
            }
          }
        }
      }
    }

    for (_ <- 0 until 250) s.tick(1.milli)
    assert(counter == 5)
    assert(s.state.tasks.isEmpty)
  }

  test("tasks sharing same runsAt should execute randomly") { implicit s =>
    var seq = Seq.empty[Int]
    for (i <- 0 until 1000) s.execute(action { seq = seq :+ i })
    s.tick()

    val expected = 0 until 1000
    assert(seq != expected)
    assertEquals(seq.sum, expected.sum)
  }

  test("execute extension method") { implicit s =>
    var wasExecuted = false
    s.executeAsync { () =>
      wasExecuted = true
    }

    assert(!wasExecuted, "should not be executed yet")
    s.tick()
    assert(wasExecuted, "was executed")
  }

  test("execute local") { implicit s =>
    var effect = 1
    s.executeTrampolined { () =>
      effect += 1

      s.executeTrampolined(() => effect += 2)
      s.executeTrampolined { () =>
        effect += 3
        s.executeTrampolined { () =>
          effect += 4
        }
      }
    }

    // No tick allowed here
    assertEquals(effect, 1 + 1 + 2 + 3 + 4)
  }

  test("execute local should fork on error") { implicit s =>
    val ex = DummyException("dummy")
    var effect = 0

    s.executeTrampolined { () =>
      effect += 1
      // Scheduling for execution
      s.executeTrampolined(() => effect += 2)
      s.executeTrampolined(() => effect += 3)
      // Subsequent effects not executed yet
      assertEquals(effect, 1)
      throw ex
    }

    // No tick allowed here
    assertEquals(effect, 1)
    assertEquals(s.state.lastReportedError, ex)

    // Other runnables have been rescheduled async
    s.tickOne()
    assertEquals(effect, 1 + 2 + 3)
  }

  test("execute local should be stack safe") { implicit s =>
    var result = 0
    def loop(n: Int): Unit =
      s.executeTrampolined { () =>
        result += 1
        if (n - 1 > 0) loop(n - 1)
      }

    val count = if (Platform.isJVM) 100000 else 10000
    loop(count)

    assertEquals(result, count)
  }

  test("start async batch, then trampolined") { implicit s =>
    var effect = 0
    s.executeAsyncBatch { () =>
      effect += 1
      s.executeTrampolined { () =>
        effect += 1
        s.executeTrampolined { () =>
          effect += 1
        }
      }
    }

    assertEquals(effect, 0)
    s.tickOne()
    assertEquals(effect, 3)
  }

  test("maxImmediateTasks") { implicit ec =>
    var result: Int = 0

    def loop(): Future[Int] =
      Future.successful(()).flatMap { _ =>
        if (result > 0) Future.successful(result)
        else loop()
      }

    val f = loop()
    ec.tick(maxImmediateTasks = Some(1000))
    assertEquals(f.value, None)

    result = 100
    ec.tick(maxImmediateTasks = Some(1000))
    assertEquals(f.value, Some(Success(100)))
  }

  def action(f: => Unit): Runnable =
    new Runnable { def run() = f }

  def delayedResult[A](delay: FiniteDuration, timeout: FiniteDuration)(r: => A)(implicit s: Scheduler) = {
    val f1 = {
      val p = Promise[A]()
      s.scheduleOnce(delay.length, delay.unit, action(p.success(r)))
      p.future
    }

    // catching the exception here, for non-useless stack traces
    val err = Try(throw new TimeoutException)
    val promise = Promise[A]()
    val task = s.scheduleOnce(timeout.length, timeout.unit, action { promise.tryComplete(err); () })

    f1.onComplete { result =>
      // canceling task to prevent waisted CPU resources and memory leaks
      // if the task has been executed already, this has no effect
      task.cancel()
      promise.tryComplete(result)
    }

    promise.future
  }
}
