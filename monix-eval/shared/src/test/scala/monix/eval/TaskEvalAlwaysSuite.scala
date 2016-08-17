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

import monix.eval.Task.{Error, Now}
import monix.execution.internal.Platform

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
      s.tick()

      t1 =!= t2
    }
  }

  test("Task.evalAlways.flatMap should be equivalent with Task.evalAlways") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.evalAlways[Int](if (1 == 1) throw ex else 1).flatMap(Task.now)
    check(t === Task.raiseError(ex))
  }

  test("Task.evalAlways.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.evalAlways(1).flatMap[Int](_ => throw ex)
    check(t === Task.raiseError(ex))
  }

  test("Task.evalAlways.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.evalAlways(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Task.evalAlways(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runAsync
    s.tick()
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

  test("Task.evalAlways.materializeAttempt should work for success") { implicit s =>
    val task = Task.evalAlways(1).materializeAttempt
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Now(1))))
  }

  test("Task.evalAlways.materializeAttempt should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.evalAlways[Int](throw dummy).materializeAttempt
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Error(dummy))))
  }

  test("Task.evalAlways.materialize should be stack safe") { implicit s =>
    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.evalAlways(n)
      else Task.evalAlways(n).materialize.flatMap {
        case Success(v) => loop(n-1)
        case Failure(ex) => Task.raiseError(ex)
      }

    val count = if (Platform.isJVM) 50000 else 5000
    val result = loop(count).runAsync; s.tick()
    assertEquals(result.value, Some(Success(0)))
  }

  test("Task.evalAlways.coeval") { implicit s =>
    val result = Task.evalAlways(100).coeval.value
    assertEquals(result, Right(100))
  }

  test("Task.evalAlways.memoize") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.memoize

    val r1 = task.runAsync
    val r2 = task.runAsync
    val r3 = task.runAsync

    assertEquals(r1.value, Some(Success(1)))
    assertEquals(r2.value, Some(Success(1)))
    assertEquals(r3.value, Some(Success(1)))
  }

  test("Task.evalAlways.memoize should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.evalAlways(1)
    for (i <- 0 until count) task = task.memoize

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalAlways.flatMap.memoize should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.evalAlways(1)
    for (i <- 0 until count) task = task.memoize.flatMap(x => Task.evalAlways(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.defer(evalAlways).memoize") { implicit s =>
    var effect = 0
    val task = Task.defer(Task.evalAlways { effect += 1; effect }).memoize

    val r1 = task.runAsync
    val r2 = task.runAsync
    val r3 = task.runAsync

    assertEquals(r1.value, Some(Success(1)))
    assertEquals(r2.value, Some(Success(1)))
    assertEquals(r3.value, Some(Success(1)))
  }
}
