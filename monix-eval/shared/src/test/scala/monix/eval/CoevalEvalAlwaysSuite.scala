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

object CoevalEvalAlwaysSuite extends BaseTestSuite {
  test("Coeval.evalAlways should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Coeval.evalAlways(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runTry
    assert(wasTriggered, "wasTriggered")
    assertEquals(f, Success("result"))
  }

  test("Coeval.evalAlways should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Coeval.evalAlways[Int](if (1 == 1) throw ex else 1).runTry

    assertEquals(f, Failure(ex))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Coeval.evalAlways.flatMap should be equivalent with Coeval.evalAlways") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.evalAlways[Int](if (1 == 1) throw ex else 1).flatMap(Coeval.now)
    check(t === Coeval.error(ex))
  }

  test("Coeval.evalAlways.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Coeval.evalAlways(1).flatMap[Int](_ => throw ex)
    check(t === Coeval.error(ex))
  }

  test("Coeval.evalAlways.map should work") { implicit s =>
    check1 { a: Int =>
      Coeval.evalAlways(a).map(_ + 1) === Coeval.evalAlways(a + 1)
    }
  }

  test("Coeval.evalAlways.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Coeval[Int] =
      Coeval.evalAlways(idx).flatMap { a =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Coeval.evalAlways(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runTry
    s.tick()
    assertEquals(f, Success(iterations * 2))
  }

  test("Coeval.evalAlways.memoize should work for first subscriber") { implicit s =>
    var effect = 0
    val task = Coeval.evalAlways { effect += 1; effect }.memoize

    val f = task.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.evalAlways.memoize should work for next subscribers") { implicit s =>
    var effect = 0
    val task = Coeval.evalAlways { effect += 1; effect }.memoize
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
    val task = Coeval.evalAlways[Int] { effect += 1; throw dummy }.memoize

    val f1 = task.runTry
    assertEquals(f1, Failure(dummy))
    val f2 = task.runTry
    assertEquals(f2, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("Coeval.evalAlways.materializeAttempt should work for success") { implicit s =>
    val task = Coeval.evalAlways(1).materializeAttempt
    val f = task.runTry
    assertEquals(f, Success(Now(1)))
  }

  test("Coeval.evalAlways.materializeAttempt should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.evalAlways[Int](throw dummy).materializeAttempt
    val f = task.runTry
    assertEquals(f, Success(Error(dummy)))
  }

  test("Coeval.evalAlways.task") { implicit s =>
    val task = Coeval.evalAlways(100).task
    assert(task.isInstanceOf[Task.EvalAlways[_]], "should be Task.EvalAlways")
    assertEquals(task.coeval.value, Right(100))
  }

  test("Coeval.EvalAlways.runTry override") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Coeval.EvalAlways { () => if (1 == 1) throw dummy }
    val f = task.runTry
    assertEquals(f, Failure(dummy))
  }

  test("Coeval.evalAlways is equivalent with Coeval.evalOnce on first run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Coeval.evalAlways { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Coeval.evalOnce { effect += 100; effect + a }
      }

      t1 === t2
    }
  }

  test("Coeval.evalAlways is not equivalent with Coeval.evalOnce on second run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Coeval.evalAlways { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Coeval.evalOnce { effect += 100; effect + a }
      }

      // Running once to trigger effects
      t1.runAttempt
      t2.runAttempt

      t1 =!= t1
    }
  }
}