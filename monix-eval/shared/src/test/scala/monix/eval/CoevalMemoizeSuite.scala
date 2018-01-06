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

import scala.util.Success

object CoevalMemoizeSuite extends BaseTestSuite {
  test("Coeval.eval.memoize should work for first subscriber") { implicit s =>
    var effect = 0
    val coeval = Coeval.eval { effect += 1; effect }.memoize

    val f = coeval.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.eval.memoize should work for next subscribers") { implicit s =>
    var effect = 0
    val coeval = Coeval.eval { effect += 1; effect }.memoize
    coeval.runTry

    val f1 = coeval.runTry
    assertEquals(f1, Success(1))
    val f2 = coeval.runTry
    assertEquals(f2, Success(1))
  }

  test("Coeval.evalOnce.memoize should work for first subscriber") { implicit s =>
    var effect = 0
    val coeval = Coeval.evalOnce { effect += 1; effect }.memoize

    val f = coeval.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.evalOnce.memoize should work for next subscribers") { implicit s =>
    var effect = 0
    val coeval = Coeval.evalOnce { effect += 1; effect }.memoize
    coeval.runTry

    val f1 = coeval.runTry
    assertEquals(f1, Success(1))
    val f2 = coeval.runTry
    assertEquals(f2, Success(1))
  }

  test("Coeval.now.memoize should return self") { implicit s =>
    assertEquals(Coeval.now(10), Coeval.now(10).memoize)
  }

  test("Coeval.error.memoize should return self") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Coeval.raiseError(dummy), Coeval.raiseError(dummy).memoize)
  }

  test("Coeval.memoize should be stack safe") { implicit s =>
    var effect = 0
    var coeval = Coeval { effect += 1; effect }
    val count = if (Platform.isJVM) 100000 else 5000
    for (_ <- 0 until count) coeval = coeval.memoize
    assertEquals(coeval.runTry, Success(1))
  }

  test("Coeval.apply.memoize effects") { implicit s =>
    var effect = 0
    val coeval1 = Coeval { effect += 1; 3 }.memoize
    val coeval2 = coeval1.map { x => effect += 1; x + 1 }

    val result1 = coeval2.runTry
    assertEquals(effect, 2)
    assertEquals(result1, Success(4))

    val result2 = coeval2.runTry
    assertEquals(effect, 3)
    assertEquals(result2, Success(4))
  }

  test("Coeval.suspend.memoize effects") { implicit s =>
    var effect = 0
    val coeval1 = Coeval.defer { effect += 1; Coeval.now(3) }.memoize
    val coeval2 = coeval1.map { x => effect += 1; x + 1 }

    val result1 = coeval2.runTry
    assertEquals(effect, 2)
    assertEquals(result1, Success(4))

    val result2 = coeval2.runTry
    assertEquals(effect, 3)
    assertEquals(result2, Success(4))
  }

  test("Coeval.suspend.flatMap.memoize effects") { implicit s =>
    var effect = 0
    val coeval1 = Coeval.defer { effect += 1; Coeval.now(2) }
      .flatMap(x => Coeval.now(x + 1)).memoize
    val coeval2 = coeval1.map { x => effect += 1; x + 1 }

    val result1 = coeval2.runTry
    assertEquals(effect, 2)
    assertEquals(result1, Success(4))

    val result2 = coeval2.runTry
    assertEquals(effect, 3)
    assertEquals(result2, Success(4))
  }
}
