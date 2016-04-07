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


import monix.eval.Task.{Now, Error}
import scala.util.{Failure, Success}

object TaskEvalOnceSuite extends BaseTestSuite {
  test("Task.evalOnce should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task.evalOnce(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.evalOnce should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.evalOnce[Int](if (1 == 1) throw ex else 1).runAsync

    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task.evalOnce.flatMap should be equivalent with Task.evalOnce") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.evalOnce[Int](if (1 == 1) throw ex else 1).flatMap(Task.now)
    check(t === Task.error(ex))
  }

  test("Task.evalOnce.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.evalOnce(1).flatMap[Int](_ => throw ex)
    check(t === Task.error(ex))
  }

  test("Task.evalOnce.map should work") { implicit s =>
    check1 { a: Int =>
      Task.evalOnce(a).map(_ + 1) === Task.evalOnce(a + 1)
    }
  }

  test("Task.evalOnce.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.evalOnce(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Task.evalOnce(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runAsync
    s.tick()
    assertEquals(f.value, Some(Success(iterations * 2)))
  }

  test("Task.evalOnce should not be cancelable") { implicit s =>
    val t = Task.evalOnce(10)
    val f = t.runAsync
    f.cancel()
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.evalOnce.memoize should work synchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.evalOnce { effect += 1; effect }.memoize

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalOnce.memoize should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.evalOnce { effect += 1; effect }.memoize
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
    val task = Task.evalOnce[Int] { effect += 1; throw dummy }.memoize

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Failure(dummy)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  test("Task.evalOnce.materializeAttempt should work for success") { implicit s =>
    val task = Task.evalOnce(1).materializeAttempt
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Now(1))))
  }

  test("Task.evalOnce.materializeAttempt should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.evalOnce[Int](throw dummy).materializeAttempt
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Error(dummy))))
  }

  test("Task.evalOnce.coeval") { implicit s =>
    val result = Task.evalOnce(100).coeval.value
    assertEquals(result, Right(100))
  }

  test("Task.EvalOnce.runAsync override") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.EvalOnce { () => if (1 == 1) throw dummy }
    val f = task.runAsync
    assertEquals(f.value, Some(Failure(dummy)))
  }
}
