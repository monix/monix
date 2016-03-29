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

import scala.util.{Try, Failure, Success}

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

  test("Task.apply should be cancelable") { implicit s =>
    val t = Task(10)
    var result = Option.empty[Try[Int]]
    val c = t.runAsync(value => result = Some(value))
    c.cancel()
    s.tick()
    assertEquals(result, None)
  }

  test("Task.apply.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task(1).flatMap[Int](_ => throw ex)
    check(t === Task.error(ex))
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

  test("Task.apply.materialize should work for success") { implicit s =>
    val task = Task.apply(1).materialize
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Now(1))))
  }

  test("Task.apply.materialize should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int](throw dummy).materialize
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Error(dummy))))
  }

  test("Task.apply.flatten is equivalent with flatMap") { implicit s =>
    check1 { a: Int =>
      val t = Task(Task.evalAlways(a))
      t.flatMap(identity) === t.flatten
    }
  }
}
