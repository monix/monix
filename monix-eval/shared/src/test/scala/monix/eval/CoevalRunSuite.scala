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

import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success, Try}

object CoevalRunSuite extends BaseTestSuite {
  def testRun(build: (() => Int) => Coeval[Int]): Unit = {
    val fa1 = build(() => 10 + 20)
    val eager1 = fa1.run

    assertEquals(eager1, Coeval.Now(30))
    assertEquals(eager1.toTry, Success(30))
    assertEquals(eager1.toEither, Right(30))
    assertEquals(eager1.run, eager1)
    assertEquals(eager1.value, 30)
    assertEquals(eager1(), 30)
    assert(eager1.isSuccess, "eager1.isSuccess")
    assert(!eager1.isError, "!eager1.isError")

    assertEquals(fa1.runAttempt, Right(30))
    assertEquals(fa1.runTry, Success(30))
    assertEquals(fa1.value, 30)

    val dummy = new DummyException("dummy")
    val fa2 = build(() => throw dummy)
    val eager2 = fa2.run

    assertEquals(eager2, Coeval.Error(dummy))
    assertEquals(eager2.toTry, Failure(dummy))
    assertEquals(eager2.toEither, Left(dummy))
    assertEquals(eager2.run, eager2)
    intercept[DummyException] { eager2.value }
    intercept[DummyException] { eager2() }
    assert(!eager2.isSuccess, "!eager2.isSuccess")
    assert(eager2.isError, "!eager2.isSuccess")

    assertEquals(fa2.runAttempt, Left(dummy))
    assertEquals(fa2.runTry, Failure(dummy))
    intercept[DummyException] { fa2.value }
  }

  test("Coeval.Always") { _ =>
    testRun(f => Coeval.eval(f()))
  }

  test("Coeval.FlatMap") { _ =>
    testRun(f => Coeval.eval(f()).flatMap(Coeval.pure))
  }

  test("Coeval.Once") { _ =>
    testRun(f => Coeval.evalOnce(f()))
  }

  test("Coeval.Eager") { _ =>
    testRun(f => Coeval.fromTry(Try(f())))
  }

  test("Eager(f)") { _ =>
    testRun(f => Coeval.Eager(f()))
  }
}
