/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.cancelables.SingleAssignCancelable
import monix.execution.schedulers.AsyncScheduler
import monix.execution.{ ExecutionModel, Scheduler, TestUtils }
import org.scalajs.macrotaskexecutor.MacrotaskExecutor

import scala.concurrent.Promise
import scala.concurrent.duration._

object AsyncSchedulerJSSuite extends TestSuite[Scheduler] with TestUtils {
  def setup() = AsyncScheduler(MacrotaskExecutor, ExecutionModel.Default)
  def tearDown(env: Scheduler): Unit = ()

  testAsync("execute async should work") { implicit s =>
    var effect = 0
    val p = Promise[Int]()

    s.execute { () =>
      effect += 1
      s.execute { () =>
        effect += 2
        s.execute { () =>
          effect += 3
          p.success(effect)
          ()
        }
      }
    }

    // Should not be executed yet
    assertEquals(effect, 0)
    for (result <- p.future) yield assertEquals(result, 1 + 2 + 3)
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
    if (isCI) {
      ignore("Test is slow and flaky on top of underpowered machines, skipping")
    }

    import concurrent.duration._
    val p = Promise[Unit]()
    val startAt = s.clockMonotonic(MILLISECONDS)
    s.scheduleOnce(100.millis) { p.success(()); () }

    for (_ <- p.future) yield {
      val duration = s.clockMonotonic(MILLISECONDS) - startAt
      assert(duration >= 100, "duration >= 100")
    }
  }

  testAsync("scheduleWithFixedRate should compensate for scheduling inaccuracy") { implicit s =>
    if (isCI) {
      ignore("Test is slow and flaky on top of underpowered machines, skipping")
    }

    import concurrent.duration._
    val p = Promise[Unit]()
    val startAt = s.clockMonotonic(MILLISECONDS)
    var count = 0

    val c = SingleAssignCancelable()
    c := s.scheduleAtFixedRate(Duration.Zero, 30.millis) {
      count += 1
      if (count == 500) {
        c.cancel()
        p.success(())
        ()
      }
    }

    for (_ <- p.future) yield {
      val duration = s.clockMonotonic(MILLISECONDS) - startAt
      val error = Math.abs(duration - 15000)
      assert(Math.abs(duration - 15000) <= 30, s"Error $error <= 30ms")
    }
  }

  test("clockRealTime") { s =>
    val t1 = System.currentTimeMillis()
    val t2 = s.clockRealTime(MILLISECONDS)
    assert(t2 >= t1, "t2 >= t1")
  }

  test("clockMonotonic") { s =>
    val t1 = System.nanoTime()
    val t2 = s.clockMonotonic(NANOSECONDS)
    assert(t2 >= t1, "t2 >= t1")
  }
}
