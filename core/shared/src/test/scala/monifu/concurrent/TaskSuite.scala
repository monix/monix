/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: https://monifu.org
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

package monifu.concurrent

import java.util.concurrent.CancellationException
import minitest.TestSuite
import monifu.concurrent.cancelables.{BooleanCancelable, CompositeCancelable}
import monifu.concurrent.schedulers.TestScheduler
import scala.util.Success

object TaskSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("map should work") { implicit s =>
    val task = Task(1 + 1).map(_ * 2)
    val f = task.unsafeRun

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(4)))
  }

  test("map cancelable should work") { implicit s =>
    val task = Task(1 + 1).map(_ * 2)
    val f = task.unsafeRun

    assert(!f.isCompleted, "f.isCompleted should be false")
    f.cancel(); s.tick()

    assert(f.value.get.failed.get.isInstanceOf[CancellationException])
  }

  test("flatten should work") { implicit s =>
    // note, this can trigger stack overflows
    def sum(n: Int): Task[Int] = {
      if (n == 0) Task.successful(0) else
        Task(n).flatMap(x => sum(x-1).map(_ + x))
    }

    val task = sum(100)
    val f = task.unsafeRun

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(5050)))
  }

  test("flatten cancelable should work, test 1") { implicit s =>
    val cancelable = BooleanCancelable()
    val task = Task.unsafeCreate[Int] { cb =>
      val c = CompositeCancelable(cancelable)
      c += Task(1).unsafeRun(cb)
      c
    }

    val f = task.flatMap(x => Task(x + 1)).unsafeRun
    assertEquals(f.value, None); f.cancel(); s.tick()
    assert(f.value.get.failed.get.isInstanceOf[CancellationException], "CancellationException")
    assert(cancelable.isCanceled, "cancelable.isCanceled")
  }

  test("flatten cancelable should work, test 2") { implicit s =>
    val cancelable = BooleanCancelable()
    val task = Task.unsafeCreate[Int] { cb =>
      val c = CompositeCancelable(cancelable)
      c += Task(1).unsafeRun(cb)
      c
    }

    val f = Task(1).flatMap(x => task.map(_ + x)).unsafeRun
    assertEquals(f.value, None)
    s.tickOne()
    assertEquals(f.value, None)
    f.cancel()

    assert(cancelable.isCanceled, "cancelable.isCanceled")
    s.tick()
    assert(f.value.get.failed.get.isInstanceOf[CancellationException], "CancellationException")
  }
}
