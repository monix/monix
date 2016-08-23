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

import monix.execution.internal.Platform
import scala.util.Success

object CoevalMemoizeSuite extends BaseTestSuite {
  test("Coeval.evalAlways.memoize should work for first subscriber") { implicit s =>
    var effect = 0
    val coeval = Coeval.evalAlways { effect += 1; effect }.memoize

    val f = coeval.runTry
    assertEquals(f, Success(1))
  }

  test("Coeval.evalAlways.memoize should work for next subscribers") { implicit s =>
    var effect = 0
    val coeval = Coeval.evalAlways { effect += 1; effect }.memoize
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
}
