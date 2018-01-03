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
import monix.execution.internal.Platform

import scala.util.{Failure, Success}

object CoevalMemoizeOnSuccessSuite extends BaseTestSuite {
  test("Coeval.eval.memoizeOnSuccess should work for first subscriber") { implicit s =>
    var effect = 0
    val coeval = Coeval.eval { effect += 1; effect }.memoizeOnSuccess

    val f = coeval.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.eval.memoizeOnSuccess should work for next subscribers") { implicit s =>
    var effect = 0
    val coeval = Coeval.eval { effect += 1; effect }.memoizeOnSuccess
    coeval.runTry

    val f1 = coeval.runTry
    assertEquals(f1, Success(1))
    val f2 = coeval.runTry
    assertEquals(f2, Success(1))
  }

  test("Coeval.evalOnce.memoizeOnSuccess should work for first subscriber") { implicit s =>
    var effect = 0
    val coeval = Coeval.evalOnce { effect += 1; effect }.memoizeOnSuccess

    val f = coeval.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.evalOnce.memoizeOnSuccess should work for next subscribers") { implicit s =>
    var effect = 0
    val coeval = Coeval.evalOnce { effect += 1; effect }.memoizeOnSuccess
    coeval.runTry

    val f1 = coeval.runTry
    assertEquals(f1, Success(1))
    val f2 = coeval.runTry
    assertEquals(f2, Success(1))
  }

  test("Coeval.now.memoizeOnSuccess should return self") { implicit s =>
    assertEquals(Coeval.now(10), Coeval.now(10).memoizeOnSuccess)
  }

  test("Coeval.error.memoizeOnSuccess should return self") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Coeval.raiseError(dummy), Coeval.raiseError(dummy).memoizeOnSuccess)
  }

  test("Coeval.memoizeOnSuccess should be stack safe") { implicit s =>
    var effect = 0
    var coeval = Coeval { effect += 1; effect }
    val count = if (Platform.isJVM) 100000 else 5000
    for (_ <- 0 until count) coeval = coeval.memoizeOnSuccess
    assertEquals(coeval.runTry, Success(1))
  }

  test("Coeval.apply.memoizeOnSuccess effects") { implicit s =>
    var effect = 0
    val coeval1 = Coeval { effect += 1; 3 }.memoizeOnSuccess
    val coeval2 = coeval1.map { x => effect += 1; x + 1 }

    val result1 = coeval2.runTry
    assertEquals(effect, 2)
    assertEquals(result1, Success(4))

    val result2 = coeval2.runTry
    assertEquals(effect, 3)
    assertEquals(result2, Success(4))
  }

  test("Coeval.suspend.memoizeOnSuccess effects") { implicit s =>
    var effect = 0
    val coeval1 = Coeval.defer { effect += 1; Coeval.now(3) }.memoizeOnSuccess
    val coeval2 = coeval1.map { x => effect += 1; x + 1 }

    val result1 = coeval2.runTry
    assertEquals(effect, 2)
    assertEquals(result1, Success(4))

    val result2 = coeval2.runTry
    assertEquals(effect, 3)
    assertEquals(result2, Success(4))
  }

  test("Coeval.suspend.flatMap.memoizeOnSuccess effects") { implicit s =>
    var effect = 0
    val coeval1 = Coeval.defer { effect += 1; Coeval.now(2) }
      .flatMap(x => Coeval.now(x + 1)).memoizeOnSuccess
    val coeval2 = coeval1.map { x => effect += 1; x + 1 }

    val result1 = coeval2.runTry
    assertEquals(effect, 2)
    assertEquals(result1, Success(4))

    val result2 = coeval2.runTry
    assertEquals(effect, 3)
    assertEquals(result2, Success(4))
  }

  test("Coeval.eval(throw).memoizeOnSuccess should not cache errors") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val coeval = Coeval
      .eval { effect += 1; if (effect < 3) throw dummy else effect }
      .memoizeOnSuccess

    assertEquals(coeval.runTry, Failure(dummy))
    assertEquals(effect, 1)
    assertEquals(coeval.runTry, Failure(dummy))
    assertEquals(effect, 2)
    assertEquals(coeval.runTry, Success(3))
    assertEquals(effect, 3)
    assertEquals(coeval.runTry, Success(3))
    assertEquals(effect, 3)
  }

  test("Coeval.eval(throw).map.memoizeOnSuccess should not cache errors") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val coeval = Coeval
      .eval { effect += 1; if (effect < 3) throw dummy else effect }
      .map(_ + 1)
      .memoizeOnSuccess

    assertEquals(coeval.runTry, Failure(dummy))
    assertEquals(effect, 1)
    assertEquals(coeval.runTry, Failure(dummy))
    assertEquals(effect, 2)
    assertEquals(coeval.runTry, Success(4))
    assertEquals(effect, 3)
    assertEquals(coeval.runTry, Success(4))
    assertEquals(effect, 3)
  }

  test("Coeval.evalOnce eq Coeval.evalOnce.memoizeOnSuccess") { implicit s =>
    val coeval = Coeval.evalOnce(1)
    assertEquals(coeval, coeval.memoizeOnSuccess)
  }

  test("Coeval.eval.memoizeOnSuccess eq Coeval.eval.memoizeOnSuccess.memoizeOnSuccess") { implicit s =>
    val coeval = Coeval.eval(1).memoizeOnSuccess
    assertEquals(coeval, coeval.memoizeOnSuccess)
  }

  test("Coeval.eval.memoize eq Coeval.eval.memoize.memoizeOnSuccess") { implicit s =>
    val coeval = Coeval.eval(1).memoize
    assertEquals(coeval, coeval.memoizeOnSuccess)
  }

  test("Coeval.eval.map.memoize eq Coeval.eval.map.memoize.memoizeOnSuccess") { implicit s =>
    val coeval = Coeval.eval(1).map(_+1).memoize
    assertEquals(coeval, coeval.memoizeOnSuccess)
  }

  test("Coeval.now.memoizeOnSuccess eq Coeval.now") { implicit s =>
    val coeval = Coeval.now(1)
    assertEquals(coeval, coeval.memoizeOnSuccess)
  }

  test("Coeval.raiseError.memoizeOnSuccess eq Coeval.raiseError") { implicit s =>
    val coeval = Coeval.raiseError(DummyException("dummy"))
    assertEquals(coeval, coeval.memoizeOnSuccess)
  }
}
