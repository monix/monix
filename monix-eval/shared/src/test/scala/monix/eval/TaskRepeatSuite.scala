/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.schedulers.TestScheduler
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

object TaskRepeatSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("repeatWithFixedDelay works") { implicit s =>
    var value = 0
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()

    val periodic = Task.repeatWithFixedDelay(10.millis, 50.millis, Task {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    sub := periodic.runAsync
    s.tick(5.seconds)
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("repeatWithFixedDelay does delays") { implicit s =>
    var value = 0
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()

    val task = {
      val ref = Task {
        if (value + 1 == 4) {
          value += 1
          sub.cancel()
          p.success(value)
        }
        else if (value < 4) {
          value += 1
        }
      }

      ref.delayExecution(1.second)
    }

    val periodic = Task.repeatWithFixedDelay(10.millis, 50.millis, task)

    sub := periodic.runAsync
    s.tick(10.millis)
    assertEquals(value, 0)
    s.tick(1.second)
    assertEquals(value, 1)
    s.tick(1.second)
    assertEquals(value, 1)
    s.tick(50.millis)
    assertEquals(value, 2)
    s.tick(1.second)
    assertEquals(value, 2)
    s.tick(50.millis)
    assertEquals(value, 3)
    s.tick(1.second)
    assertEquals(value, 3)
    s.tick(50.millis)
    assertEquals(value, 4)
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("repeatAtFixedRate works") { implicit s =>
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    val periodic = Task.repeatAtFixedRate(10.millis, 50.millis, Task {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    sub := periodic.runAsync
    s.tick(5.seconds)
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("repeatAtFixedRate has no overlap") { implicit s =>
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    val task = {
      val ref = Task {
        if (value + 1 == 4) {
          value += 1
          sub.cancel()
          p.success(value)
        }
        else if (value < 4) {
          value += 1
        }
      }

      ref.delayExecution(1.second)
    }

    val periodic = Task.repeatAtFixedRate(10.millis, 50.millis, task)

    sub := periodic.runAsync
    s.tick(10.millis)
    assertEquals(value, 0)
    s.tick(1.second)
    assertEquals(value, 1)
    s.tick(1.second)
    assertEquals(value, 2)
    s.tick(1.second)
    assertEquals(value, 3)
    s.tick(1.second)
    assertEquals(value, 4)
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("repeatAtFixedRate is stack safe") { implicit s =>
    var value = 0
    val periodic = Task.repeatAtFixedRate(100.millis, 100.millis, Task.eval(value += 1))

    val f = periodic.runAsync
    s.tick(10.hours)
    f.cancel()
    assertEquals(value, 10 * 60 * 60 * 10)
  }

  test("repeatWithFixedDelay is stack safe") { implicit s =>
    var value = 0
    val periodic = Task.repeatWithFixedDelay(100.millis, 100.millis, Task.eval(value += 1))

    val f = periodic.runAsync
    s.tick(10.hours)
    f.cancel()
    assertEquals(value, 10 * 60 * 60 * 10)
  }
}
