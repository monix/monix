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

import scala.util.{Failure, Random, Success}

object CoevalMiscSuite extends BaseTestSuite {
  test("Coeval.now.failed should end in error") { implicit s =>
    val result = Coeval.now(1).failed.runTry
    assert(result.isFailure &&
      result.failed.get.isInstanceOf[NoSuchElementException],
      "Should throw NoSuchElementException")
  }

  test("Coeval.raiseError.failed should expose error") { implicit s =>
    val ex = DummyException("dummy")
    val result = Coeval.raiseError[Int](ex).failed.runTry
    assertEquals(result, Success(ex))
  }

  test("Coeval.map protects against user code") { implicit s =>
    val ex = DummyException("dummy")
    val result = Coeval.now(1).map(_ => throw ex).runTry
    assertEquals(result, Failure(ex))
  }

  test("Coeval.now.dematerializeAttempt") { implicit s =>
    val result = Coeval.now(1).materializeAttempt.dematerializeAttempt.runTry
    assertEquals(result, Success(1))
  }

  test("Coeval.raiseError.dematerializeAttempt") { implicit s =>
    val ex = DummyException("dummy")
    val result = Coeval.raiseError[Int](ex).materializeAttempt.dematerializeAttempt.runTry
    assertEquals(result, Failure(ex))
  }

  test("Coeval.restartUntil") { implicit s =>
    val r = Coeval(Random.nextInt()).restartUntil(_ % 2 == 0).value
    assert(r % 2 == 0, "should be divisible with 2")
  }

  test("Coeval.pure is an alias of now") { implicit s =>
    assertEquals(Coeval.pure(1), Coeval.now(1))
  }
}
