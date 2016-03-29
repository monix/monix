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

package monix.async

import monix.async.Task.{Error, Now}

import scala.util.{Failure, Success}

object TaskEvalAlwaysSuite extends BaseTestSuite {
  test("Task.evalAlways should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task.evalAlways(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.evalAlways should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.evalAlways[Int](if (1 == 1) throw ex else 1).runAsync

    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task.evalAlways is equivalent with Task.evalOnce on first run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task.evalAlways { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.evalOnce { effect += 100; effect + a }
      }

      t1 === t2
    }
  }

  test("Task.evalAlways is not equivalent with Task.evalOnce on second run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task.evalAlways { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.evalOnce { effect += 100; effect + a }
      }

      // Running once to trigger effects
      t1.runAsync(s)
      t2.runAsync(s)

      t1 =!= t1
    }
  }

  test("Task.evalAlways.flatMap should be equivalent with Task.evalAlways") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.evalAlways[Int](if (1 == 1) throw ex else 1).flatMapAsync(Task.now)
    check(t === Task.error(ex))
  }

  test("Task.evalAlways.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.evalAlways(1).flatMapAsync[Int](_ => throw ex)
    check(t === Task.error(ex))
  }

  test("Task.evalAlways should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.evalAlways(idx).flatMapAsync { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Task.evalAlways(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runAsync
    assertEquals(f.value, Some(Success(iterations * 2)))
  }

  test("Task.evalAlways should not be cancelable") { implicit s =>
    val t = Task.evalAlways(10)
    val f = t.runAsync
    f.cancel()
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.evalAlways.memoize should work synchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.memoize

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalAlways.memoize should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.memoize
    task.runAsync
    s.tick()

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.evalAlways(error).memoize should work") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.evalAlways[Int] { effect += 1; throw dummy }.memoize

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Failure(dummy)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  test("Task.evalAlways.materialize should work for success") { implicit s =>
    val task = Task.evalAlways(1).materialize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Now(1))))
  }

  test("Task.evalAlways.materialize should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.evalAlways[Int](throw dummy).materialize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Error(dummy))))
  }
}
