/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
import monix.execution.Cancelable
import scala.concurrent.duration._

object ReferenceSchedulerSuite extends SimpleTestSuite {
  class DummyScheduler(
    val underlying: TestScheduler = TestScheduler())
    extends ReferenceScheduler {

    def executionModel = ExecutionModel.Default
    def tick(time: FiniteDuration = Duration.Zero) = underlying.tick(time)
    def state = underlying.statePula
    def execute(runnable: Runnable): Unit = underlying.execute(runnable)
    def reportFailure(t: Throwable): Unit = underlying.reportFailure(t)
    def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable =
      underlying.scheduleOnce(initialDelay, unit, r)
  }

  test("current time") {
    val s = new DummyScheduler
    assert(s.currentTimeMillis() > 0)
  }

  test("schedule with fixed delay") {
    val s = new DummyScheduler
    var effect = 0

    val task = s.scheduleWithFixedDelay(1.second, 2.seconds) { effect += 1 }

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 3)
    s.tick(1.second)
    task.cancel()
    s.tick(1.second)
    assertEquals(effect, 3)
  }

  test("schedule at fixed rate") {
    val s = new DummyScheduler
    var effect = 0
    val task = s.scheduleAtFixedRate(1.second, 2.seconds) { effect += 1 }

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 2)
    s.tick(1.seconds)
    assertEquals(effect, 3)
    s.tick(1.second)
    task.cancel()
    s.tick(1.second)
    assertEquals(effect, 3)
  }
}
