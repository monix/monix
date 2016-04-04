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

package monix.eval

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskDelaySuite extends BaseTestSuite {
  test("Task#delayExecution should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayExecution(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#delayExecution is cancelable") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayExecution(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assert(!wasTriggered, "!wasTriggered")

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty,
      "should cancel the scheduleOnce(delay) as well")
  }

  test("Task#delayExecutionWith should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val delayTask = Task.now(0).delayExecution(1.second)
    val task = Task(trigger()).delayExecutionWith(delayTask)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#delayExecutionWith is cancelable") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val delayTask = Task.now(0).delayExecution(1.second)
    val task = Task(trigger()).delayExecutionWith(delayTask)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assert(!wasTriggered, "!wasTriggered")

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty,
      "should cancel the scheduleOnce(delay) as well")
  }

  test("Task#delayExecutionWith should handle error") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val dummy = DummyException("dummy")
    val delayTask = Task(Task.error[Int](dummy)).flatten
    val task = Task(trigger()).delayExecutionWith(delayTask)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task#delayResult should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayResult(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#delayResult is cancelable") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayResult(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty,
      "should cancel the scheduleOnce(delay) as well")
  }

  test("Task#delayResultBySelector should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val delayTask = Task.now(0).delayExecution(1.second)
    val task = Task(trigger()).delayResultBySelector(a => delayTask)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#delayResultBySelector is cancelable") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val delayTask = Task.now(0).delayExecution(1.second)
    val task = Task(trigger()).delayResultBySelector(a => delayTask)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty,
      "should cancel the scheduleOnce(delay) as well")
  }

  test("Task#delayResultBySelector should handle error") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val dummy = DummyException("dummy")
    val delayTask = Task(Task.error[Int](dummy)).flatten
    val task = Task(trigger()).delayResultBySelector(a => delayTask)

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task#delayResultBySelector should protect against user code") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val dummy = DummyException("dummy")
    val task = Task(trigger()).delayResultBySelector(a => throw dummy)

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task#delayResultBySelector should mirror error") { implicit s =>
    var wasTriggered = false
    val dummy = DummyException("dummy")
    def trigger(): String = { wasTriggered = true; throw dummy }

    val task = Task(trigger()).delayResultBySelector(a => Task.now(1).delayExecution(1.second))

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Failure(dummy)))
  }
}
