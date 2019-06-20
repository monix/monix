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

package monix.eval

import minitest.TestSuite
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler

object TaskCoevalForeachSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("Task.foreachL") { implicit s =>
    var effect = 0
    val task = Task.evalAsync(1).foreachL(x => effect += x)

    assertEquals(effect, 0)
    task.runToFuture; s.tick()
    assertEquals(effect, 1)
    task.runToFuture; s.tick()
    assertEquals(effect, 2)
  }

  test("Task.foreach") { implicit s =>
    var effect = 0
    val task = Task.evalAsync(1)

    assertEquals(effect, 0)
    task.foreach(x => effect += x); s.tick()
    assertEquals(effect, 1)
    task.foreach(x => effect += x); s.tick()
    assertEquals(effect, 2)
  }

  test("Task.foreach reports exceptions using scheduler") { implicit s =>
    val dummy = DummyException("dummy")
    Task.evalAsync(1).foreach(_ => throw dummy)
    s.tick()
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Coeval.foreachL") { _ =>
    var effect = 0
    val coeval = Coeval(1).foreachL(x => effect += x)

    assertEquals(effect, 0)
    coeval.value()
    assertEquals(effect, 1)
    coeval.value()
    assertEquals(effect, 2)
  }

  test("Coeval.foreach") { _ =>
    var effect = 0
    val coeval = Coeval(1)

    assertEquals(effect, 0)
    coeval.foreach(x => effect += x)
    assertEquals(effect, 1)
    coeval.foreach(x => effect += x)
    assertEquals(effect, 2)
  }
}
