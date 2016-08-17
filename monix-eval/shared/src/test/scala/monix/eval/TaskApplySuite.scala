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

object TaskApplySuite extends BaseTestSuite {
  test("Task.apply should work, on different thread") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.apply should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](if (1 == 1) throw ex else 1).runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task.apply is equivalent with Task.evalAlways") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.evalAlways { effect += 100; effect + a }
      }

      List(t1 === t2, t2 === t1)
    }
  }

  test("Task.apply is equivalent with Task.evalOnce on first run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.evalOnce { effect += 100; effect + a }
      }

      t1 === t2
    }
  }

  test("Task.apply is not equivalent with Task.evalOnce on second run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task { effect += 100; effect + a }
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

  test("Task.apply.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task(1).flatMap[Int](_ => throw ex)
    check(t === Task.raiseError(ex))
  }

  test("Task.apply should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.apply(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Task.apply(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runAsync
    s.tick()
    assertEquals(f.value, Some(Success(iterations * 2)))
  }

  test("Task.apply.memoize should work asynchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task { effect += 1; effect }.memoize
      .flatMap(Task.now).flatMap(Task.now)

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.memoize should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task { effect += 1; effect }.memoize
      .flatMap(Task.now).flatMap(Task.now)

    task.runAsync
    s.tick()

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
  }


  test("Task.apply.memoize should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task(1)
    for (i <- 0 until count) task = task.memoize

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.flatMap.memoize should be stack safe, test 1") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task(1)
    for (i <- 0 until count) task = task.memoize.flatMap(x => Task.now(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.flatMap.memoize should be stack safe, test 2") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task(1)
    for (i <- 0 until count) task = task.memoize.flatMap(x => Task(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply(error).memoize should work") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.apply[Int] { effect += 1; throw dummy }.memoize
      .flatMap(Task.now).flatMap(Task.now)

    val f1 = task.runAsync; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  test("Task.apply.materializeAttempt should work for success") { implicit s =>
    val task = Task.apply(1).materializeAttempt
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Now(1))))
  }

  test("Task.apply.materializeAttempt should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int] { throw dummy }.materializeAttempt
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Error(dummy))))
  }

  test("Task.apply.materialize should be stack safe") { implicit s =>
    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.apply(n)
      else Task.apply(n).materialize.flatMap {
        case Success(v) => loop(n-1)
        case Failure(ex) => Task.raiseError(ex)
      }

    val count = if (Platform.isJVM) 50000 else 5000
    val result = loop(count).runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0)))
  }

  test("Task.apply.flatten is equivalent with flatMap") { implicit s =>
    check1 { a: Int =>
      val t = Task(Task.evalAlways(a))
      t.flatMap(identity) === t.flatten
    }
  }

  test("Task.apply.coeval") { implicit s =>
    val result = Task(100).coeval.value
    assert(result.isLeft, "result.isLeft")
    assertEquals(result.left.map(_.value), Left(None))
    s.tick()
    assertEquals(result.left.map(_.value), Left(Some(Success(100))))
  }
}
