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

object CoevalEvalAlwaysSuite extends BaseTestSuite {
  test("Coeval.eval should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Coeval.eval(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runTry
    assert(wasTriggered, "wasTriggered")
    assertEquals(f, Success("result"))
  }

  test("Coeval.eval should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Coeval.eval[Int](if (1 == 1) throw ex else 1).runTry

    assertEquals(f, Failure(ex))
    assertEquals(s.state.lastReportedError, null)
  }

  test("Coeval.eval.flatMap should be equivalent with Coeval.eval") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.eval[Int](if (1 == 1) throw ex else 1).flatMap(Coeval.now)
    check(t <-> Coeval.raiseError(ex))
  }

  test("Coeval.eval.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.eval(1).flatMap[Int](_ => throw ex)
    check(t <-> Coeval.raiseError(ex))
  }

  test("Coeval.eval.map should work") { implicit s =>
    check1 { a: Int =>
      Coeval.eval(a).map(_ + 1) <-> Coeval.eval(a + 1)
    }
  }

  test("Coeval.eval.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Coeval[Int] =
      Coeval.eval(idx).flatMap { _ =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Coeval.eval(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runTry
    s.tick()
    assertEquals(f, Success(iterations * 2))
  }

  test("Coeval.eval(error).memoize should work") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Coeval.eval[Int] { effect += 1; throw dummy }.memoize

    val f1 = task.runTry
    assertEquals(f1, Failure(dummy))
    val f2 = task.runTry
    assertEquals(f2, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("Coeval.eval.materialize should work for success") { implicit s =>
    val task = Coeval.eval(1).materialize
    val f = task.runTry
    assertEquals(f, Success(Success(1)))
  }

  test("Coeval.eval.materialize should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.eval[Int](throw dummy).materialize
    val f = task.runTry
    assertEquals(f, Success(Failure(dummy)))
  }

  test("Coeval.eval.task") { implicit s =>
    val task = Coeval.eval(100).task
    assertEquals(task.coeval.value, Right(100))
  }

  test("Coeval.EvalAlways.runTry override") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.eval { if (1 == 1) throw dummy else 10 }
    val f = task.runTry
    assertEquals(f, Failure(dummy))
  }

  test("Coeval.eval is equivalent with Coeval.evalOnce on first run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Coeval.eval { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Coeval.evalOnce { effect += 100; effect + a }
      }

      t1 <-> t2
    }
  }
}
