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

object CoevalEvalOnceSuite extends BaseTestSuite {
  test("Coeval.evalOnce should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Coeval.evalOnce(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runTry
    assert(wasTriggered, "wasTriggered")
    assertEquals(f, Success("result"))
  }

  test("Coeval.evalOnce should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Coeval.evalOnce[Int](if (1 == 1) throw ex else 1).runTry

    assertEquals(f, Failure(ex))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Coeval.evalOnce.flatMap should be equivalent with Coeval.evalOnce") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.evalOnce[Int](if (1 == 1) throw ex else 1).flatMap(Coeval.now)
    check(t === Coeval.error(ex))
  }

  test("Coeval.evalOnce.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.evalOnce(1).flatMap[Int](_ => throw ex)
    check(t === Coeval.error(ex))
  }

  test("Coeval.evalOnce.map should work") { implicit s =>
    check1 { a: Int =>
      Coeval.evalOnce(a).map(_ + 1) === Coeval.evalOnce(a + 1)
    }
  }

  test("Coeval.evalOnce.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Coeval[Int] =
      Coeval.evalOnce(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Coeval.evalOnce(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runTry
    s.tick()
    assertEquals(f, Success(iterations * 2))
  }

  test("Coeval.evalOnce.memoize should work for first subscriber") { implicit s =>
    var effect = 0
    val task = Coeval.evalOnce { effect += 1; effect }.memoize

    val f = task.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.evalOnce.memoize should work for next subscribers") { implicit s =>
    var effect = 0
    val task = Coeval.evalOnce { effect += 1; effect }.memoize
    task.runTry
    s.tick()

    val f1 = task.runTry
    assertEquals(f1, Success(1))
    val f2 = task.runTry
    assertEquals(f2, Success(1))
  }

  test("Coeval.evalAlways(error).memoize should work") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Coeval.evalOnce[Int] { effect += 1; throw dummy }.memoize

    val f1 = task.runTry
    assertEquals(f1, Failure(dummy))
    val f2 = task.runTry
    assertEquals(f2, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("Coeval.evalOnce.materializeAttempt should work for success") { implicit s =>
    val task = Coeval.evalOnce(1).materializeAttempt
    val f = task.runTry
    assertEquals(f, Success(Now(1)))
  }

  test("Coeval.evalOnce.materializeAttempt should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.evalOnce[Int](throw dummy).materializeAttempt
    val f = task.runTry
    assertEquals(f, Success(Error(dummy)))
  }

  test("Coeval.evalOnce.task") { implicit s =>
    val task = Coeval.evalOnce(100).task
    assert(task.isInstanceOf[Task.EvalOnce[_]], "should be Task.EvalOnce")
    assertEquals(task.coeval.value, Right(100))
  }

  test("Coeval.EvalOnce.runTry override") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.EvalOnce { () => if (1 == 1) throw dummy }
    val f = task.runTry
    assertEquals(f, Failure(dummy))
  }
}
