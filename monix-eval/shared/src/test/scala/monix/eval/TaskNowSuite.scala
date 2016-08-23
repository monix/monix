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

import scala.util.{Failure, Success}

object TaskNowSuite extends BaseTestSuite {
  test("Task.now should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task.now(trigger())
    assert(wasTriggered, "wasTriggered")
    val f = task.runAsync
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.error should work synchronously") { implicit s =>
    var wasTriggered = false
    val dummy = DummyException("dummy")
    def trigger(): Throwable = { wasTriggered = true; dummy }

    val task = Task.raiseError(trigger())
    assert(wasTriggered, "wasTriggered")
    val f = task.runAsync
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.now.map should work") { implicit s =>
    Coeval.now(1).map(_ + 1).value
    check1 { a: Int =>
      Task.now(a).map(_ + 1) === Task.now(a + 1)
    }
  }

  test("Task.error.map should be the same as Task.error") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      Task.raiseError[Int](dummy).map(_ + 1) === Task.raiseError(dummy)
    }
  }

  test("Task.error.flatMap should be the same as Task.flatMap") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      Task.raiseError[Int](dummy).flatMap(Task.now) === Task.raiseError(dummy)
    }
  }

  test("Task.error.flatMap should be protected") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      val err = DummyException("err")
      Task.raiseError[Int](dummy).flatMap[Int](_ => throw err) === Task.raiseError(dummy)
    }
  }

  test("Task.now.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.now(1).flatMap[Int](_ => throw ex)
    check(t === Task.raiseError(ex))
  }

  test("Task.now.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.now(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Task.now(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runAsync

    s.tickOne()
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(iterations * 2)))
  }

  test("Task.now should not be cancelable") { implicit s =>
    val t = Task.now(10)
    val f = t.runAsync
    f.cancel()
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.error should not be cancelable") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.raiseError(dummy)
    val f = t.runAsync
    f.cancel()
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.now.coeval") { implicit s =>
    val result = Task.now(100).coeval.value
    assertEquals(result, Right(100))
  }

  test("Task.error.coeval") { implicit s =>
    val dummy = DummyException("dummy")
    val result = Task.raiseError(dummy).coeval.runTry
    assertEquals(result, Failure(dummy))
  }
}
