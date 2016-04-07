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

import monix.eval.Coeval.{Error, Now}

import scala.util.{Failure, Success}

object CoevalNowSuite extends BaseTestSuite {
  test("Coeval.now should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Coeval.now(trigger())
    assert(wasTriggered, "wasTriggered")
    val r = task.runTry
    assertEquals(r, Success("result"))
  }

  test("Coeval.error should work synchronously") { implicit s =>
    var wasTriggered = false
    val dummy = DummyException("dummy")
    def trigger(): Throwable = { wasTriggered = true; dummy }

    val task = Coeval.error(trigger())
    assert(wasTriggered, "wasTriggered")
    val r = task.runTry
    assertEquals(r, Failure(dummy))
  }

  test("Coeval.now.map should work") { implicit s =>
    Coeval.now(1).map(_ + 1).value
    check1 { a: Int =>
      Coeval.now(a).map(_ + 1) === Coeval.now(a + 1)
    }
  }

  test("Coeval.error.map should be the same as Coeval.error") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      Coeval.error[Int](dummy).map(_ + 1) === Coeval.error[Int](dummy)
    }
  }

  test("Coeval.error.flatMap should be the same as Coeval.flatMap") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      Coeval.error[Int](dummy).flatMap(Coeval.now) === Coeval.error(dummy)
    }
  }

  test("Coeval.error.flatMap should be protected") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      val err = DummyException("err")
      Coeval.error[Int](dummy).flatMap[Int](_ => throw err) === Coeval.error(dummy)
    }
  }

  test("Coeval.now.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.now(1).flatMap[Int](_ => throw ex)
    check(t === Coeval.error(ex))
  }

  test("Coeval.now.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Coeval[Int] =
      Coeval.now(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Coeval.now(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val r = loop(iterations, 0).runTry
    assertEquals(r, Success(iterations * 2))
  }

  test("Coeval.now.memoize should return self") { implicit s =>
    assertEquals(Coeval.now(10), Coeval.now(10).memoize)
  }

  test("Coeval.error.memoize should return self") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Coeval.error(dummy), Coeval.error(dummy).memoize)
  }

  test("Coeval.now.materialize should work") { implicit s =>
    val task = Coeval.now(1).materialize
    assertEquals(task.value, Success(1))
  }

  test("Coeval.now.materializeAttempt should work") { implicit s =>
    val task = Coeval.now(1).materializeAttempt
    assertEquals(task.value, Now(1))
  }

  test("Coeval.error.materialize should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.error(dummy).materialize
    assertEquals(task.value, Failure(dummy))
  }

  test("Coeval.error.materializeAttempt should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.error(dummy).materializeAttempt
    assertEquals(task.value, Error(dummy))
  }

  test("Coeval.now.task should equal Task.now") { implicit s =>
    val coeval = Coeval.now(100).task
    val task = Task.now(100)
    assertEquals(coeval, task)
  }

  test("Coeval.error.task should equal Task.error") { implicit s =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.error(dummy).task
    val task = Task.error(dummy)
    assertEquals(coeval, task)
  }
}
