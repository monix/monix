/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import scala.util.{ Failure, Success }

class CoevalMiscSuite extends BaseTestSuite {
  fixture.test("Coeval.now.attempt should succeed") { _ =>
    val result = Coeval.now(1).attempt.value()
    assertEquals(result, Right(1))
  }

  fixture.test("Coeval.raiseError.attempt should expose error") { _ =>
    val ex = DummyException("dummy")
    val result = Coeval.raiseError[Int](ex).attempt.value()
    assertEquals(result, Left(ex))
  }

  fixture.test("Coeval.fail should expose error") { _ =>
    val dummy = DummyException("dummy")
    check1 { (fa: Coeval[Int]) =>
      val r = fa.map(_ => throw dummy).failed.value()
      r == dummy
    }
  }

  fixture.test("Coeval.fail should fail for successful values") { _ =>
    intercept[NoSuchElementException] {
      Coeval.eval(10).failed.value()
      ()
    }
    ()
  }

  fixture.test("Coeval.map protects against user code") { _ =>
    val ex = DummyException("dummy")
    val result = Coeval.now(1).map(_ => throw ex).runTry()
    assertEquals(result, Failure(ex))
  }

  fixture.test("Coeval.now.dematerialize") { _ =>
    val result = Coeval.now(1).materialize.dematerialize.runTry()
    assertEquals(result, Success(1))
  }

  fixture.test("Coeval.raiseError.dematerialize") { _ =>
    val ex = DummyException("dummy")
    val result = Coeval.raiseError[Int](ex).materialize.dematerialize.runTry()
    assertEquals(result, Failure(ex))
  }

  fixture.test("Coeval.restartUntil") { _ =>
    var i = 0
    val r = Coeval {
      i += 1; i
    }.restartUntil(_ > 10).value()
    assertEquals(r, 11)
  }

  fixture.test("Coeval.pure is an alias of now") { _ =>
    assertEquals(Coeval.pure(1), Coeval.now(1))
  }

  test("Coeval.delay is an alias of eval") {
    var i = 0
    val fa = Coeval.delay { i += 1; i }

    assertEquals(fa.value(), 1)
    assertEquals(fa.value(), 2)
    assertEquals(fa.value(), 3)
  }

  fixture.test("Coeval.flatten is equivalent with flatMap") { _ =>
    check1 { (nr: Int) =>
      val ref = Coeval(Coeval(nr))
      ref.flatten <-> ref.flatMap(x => x)
    }
  }

  fixture.test("Coeval.error.flatten is equivalent with flatMap") { _ =>
    val ex = DummyException("dummy")
    val ref = Coeval(Coeval.raiseError[Int](ex))
    assertEquals(ref.flatten.runTry(), ref.flatMap(x => x).runTry())
  }
}
