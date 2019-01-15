/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.execution.internal

import minitest.TestSuite
import monix.execution.atomic.Atomic
import monix.execution.schedulers.{AsyncScheduler, StandardContext}
import monix.execution.{Cancelable, ExecutionModel, Scheduler, UncaughtExceptionReporter}

import scala.concurrent.Promise
import scala.concurrent.duration._

object AsyncSchedulerSuite extends TestSuite[Scheduler] {
  val lastReported = Atomic(null : Throwable)
  val reporter = new StandardContext(new UncaughtExceptionReporter {
    def reportFailure(ex: Throwable): Unit =
      lastReported set ex
  })

  def setup(): Scheduler = {
    lastReported.set(null)
    AsyncScheduler(reporter, ExecutionModel.Default)
  }

  def tearDown(env: Scheduler): Unit =
    lastReported.set(null)

  testAsync("execute async should work") { implicit s =>
    var effect = 0
    val p = Promise[Int]()

    s.executeAsync { () =>
      effect += 1
      s.executeAsync { () =>
        effect += 2
        s.executeAsync { () =>
          effect += 3
          p.success(effect)
        }
      }
    }

    // Should not be executed yet
    assertEquals(effect, 0)
    for (result <- p.future) yield
      assertEquals(result, 1 + 2 + 3)
  }

  test("execute local should work") { implicit s =>
    var effect = 0

    s.executeTrampolined { () =>
      effect += 1
      s.executeTrampolined { () =>
        effect += 2
        s.executeTrampolined { () =>
          effect += 3
        }
      }
    }

    assertEquals(effect, 1 + 2 + 3)
  }

  testAsync("schedule for execution with delay") { implicit s =>
    import concurrent.duration._
    val p = Promise[Unit]()
    val startAt = s.clockMonotonic(MILLISECONDS)
    s.scheduleOnce(100.millis)(p.success(()))

    for (_ <- p.future) yield {
      val duration = s.clockMonotonic(MILLISECONDS) - startAt
      assert(duration >= 100, "duration >= 100")
    }
  }

  testAsync("scheduleWithFixedRate should compensate for scheduling inaccuracy") { implicit s =>
    import concurrent.duration._
    val p = Promise[Unit]()
    val startAt = s.clockMonotonic(MILLISECONDS)
    var count = 0
    lazy val c: Cancelable = s.scheduleAtFixedRate(Duration.Zero, 20.millis) {
      count += 1
      if (count == 500) {
        c.cancel()
        p.success(())
      }
    }
    c // trigger evaluation

    for (_ <- p.future) yield {
      val duration = s.clockMonotonic(MILLISECONDS) - startAt
      assert(Math.abs(duration - 10000) <= 20, "Error <= 20ms")
    }
  }

  test("clockRealTime") { s =>
    val t1 = System.currentTimeMillis()
    val t2 = s.clockRealTime(MILLISECONDS)
    assert(t2 >= t1, "t2 >= t1")
  }

  test("clockMonotonic") {  s =>
    val t1 = System.nanoTime()
    val t2 = s.clockMonotonic(NANOSECONDS)
    assert(t2 >= t1, "t2 >= t1")
  }
}
