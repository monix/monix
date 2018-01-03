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

package monix.execution.internal

import minitest.TestSuite
import monix.execution.atomic.Atomic
import monix.execution.{ExecutionModel, Scheduler, UncaughtExceptionReporter}
import monix.execution.schedulers.AsyncScheduler

import scala.concurrent.Promise

object AsyncSchedulerSuite extends TestSuite[Scheduler] {
  val lastReported = Atomic(null : Throwable)
  val reporter = new UncaughtExceptionReporter {
    def reportFailure(ex: Throwable): Unit =
      lastReported set ex
  }

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
    val startAt = s.currentTimeMillis()
    s.scheduleOnce(100.millis)(p.success(()))

    for (_ <- p.future) yield {
      val duration = s.currentTimeMillis() - startAt
      assert(duration >= 100, "duration >= 100")
    }
  }
}
