/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import cats.laws._
import cats.laws.discipline._

import monix.execution.exceptions.DummyException
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

  test("Coeval.now.isSuccess") { implicit s =>
    assert(Coeval.Now(1).isSuccess, "isSuccess")
    assert(!Coeval.Now(1).isError, "!isFailure")
  }

  test("Coeval.error should work synchronously") { implicit s =>
    var wasTriggered = false
    val dummy = DummyException("dummy")
    def trigger(): Throwable = { wasTriggered = true; dummy }

    val task = Coeval.raiseError(trigger())
    assert(wasTriggered, "wasTriggered")
    val r = task.runTry
    assertEquals(r, Failure(dummy))
  }

  test("Coeval.now.map should work") { implicit s =>
    Coeval.now(1).map(_ + 1).value
    check1 { a: Int =>
      Coeval.now(a).map(_ + 1) <-> Coeval.now(a + 1)
    }
  }

  test("Coeval.error.map should be the same as Coeval.error") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      Coeval.raiseError[Int](dummy).map(_ + 1) <-> Coeval.raiseError[Int](dummy)
    }
  }

  test("Coeval.error.flatMap should be the same as Coeval.flatMap") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      Coeval.raiseError[Int](dummy).flatMap(Coeval.now) <-> Coeval.raiseError(dummy)
    }
  }

  test("Coeval.error.flatMap should be protected") { implicit s =>
    check {
      val dummy = DummyException("dummy")
      val err = DummyException("err")
      Coeval.raiseError[Int](dummy).flatMap[Int](_ => throw err) <-> Coeval.raiseError(dummy)
    }
  }

  test("Coeval.now.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.now(1).flatMap[Int](_ => throw ex)
    check(t <-> Coeval.raiseError(ex))
  }

  test("Coeval.now.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Coeval[Int] =
      Coeval.now(idx).flatMap { _ =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Coeval.now(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val r = loop(iterations, 0).runTry
    assertEquals(r, Success(iterations * 2))
  }

  test("Coeval.now.materialize should work") { implicit s =>
    val task = Coeval.now(1).materialize
    assertEquals(task.value, Success(1))
  }


  test("Coeval.error.materialize should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.raiseError(dummy).materialize
    assertEquals(task.value, Failure(dummy))
  }
}
