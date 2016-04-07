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

import scala.concurrent.TimeoutException
import scala.util.{Failure, Success}

object CoevalErrorSuite extends BaseTestSuite {
  test("Coeval.failed should expose error") { implicit s =>
    val dummy = DummyException("ex")
    val r = Coeval.error[Int](dummy).failed.runTry
    assertEquals(r, Success(dummy))
  }

  test("Coeval.failed should end in error on success") { implicit s =>
    intercept[NoSuchElementException] {
      Coeval.now(10).failed.value
    }
  }

  test("Coeval.now.materialize") { implicit s =>
    assertEquals(Coeval.now(10).materialize.value, Success(10))
  }

  test("Coeval.error.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Coeval.error[Int](dummy).materialize.value, Failure(dummy))
  }

  test("Coeval.evalOnce.materialize") { implicit s =>
    assertEquals(Coeval.evalOnce(10).materialize.value, Success(10))
  }

  test("Coeval.evalAlways.materialize") { implicit s =>
    assertEquals(Coeval.evalAlways(10).materialize.value, Success(10))
  }

  test("Coeval.defer.materialize") { implicit s =>
    assertEquals(Coeval.defer(Coeval.now(10)).materialize.value, Success(10))
  }

  test("Coeval.defer.flatMap.materialize") { implicit s =>
    assertEquals(Coeval.defer(Coeval.now(10)).flatMap(Coeval.now).materialize.value, Success(10))
  }

  test("Coeval.error.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Coeval.error[Int](dummy).materialize.value, Failure(dummy))
  }

  test("Coeval.flatMap.materialize") { implicit s =>
    assertEquals(Coeval.evalAlways(10).flatMap(x => Coeval.now(x))
      .materialize.runTry, Success(Success(10)))
  }

  test("Coeval.now.flatMap(error).materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val r = Coeval.now(10).flatMap[Int](_ => throw dummy).materialize
    assertEquals(r.runTry, Success(Failure(dummy)))
  }

  test("Coeval.defer(error).materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Coeval.defer[Int](throw dummy).materialize
    assertEquals(f.runTry, Success(Failure(dummy)))
  }

  test("Coeval.defer(error).flatMap.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Coeval.defer[Int](throw dummy).flatMap(Coeval.now).materialize
    assertEquals(f.runTry, Success(Failure(dummy)))
  }

  test("Coeval.now.dematerialize") { implicit s =>
    val result = Coeval.now(10).materialize.dematerialize.runTry
    assertEquals(result, Success(10))
  }

  test("Coeval.error.dematerialize") { implicit s =>
    val dummy = DummyException("dummy")
    val result = Coeval.error[Int](dummy).materialize.dematerialize.runTry
    assertEquals(result, Failure(dummy))
  }

  test("Coeval.now.dematerializeAttempt") { implicit s =>
    val result = Coeval.now(10).materializeAttempt.dematerializeAttempt.runTry
    assertEquals(result, Success(10))
  }

  test("Coeval.error.dematerializeAttempt") { implicit s =>
    val dummy = DummyException("dummy")
    val result = Coeval.error[Int](dummy).materializeAttempt.dematerializeAttempt.runTry
    assertEquals(result, Failure(dummy))
  }

  test("Coeval#onErrorRecover should mirror source on success") { implicit s =>
    val coeval = Coeval(1).onErrorRecover { case ex: Throwable => 99 }
    assertEquals(coeval.runTry, Success(1))
  }

  test("Coeval#onErrorRecover should recover") { implicit s =>
    val ex = DummyException("dummy")
    val coeval = Coeval[Int](if (1 == 1) throw ex else 1).onErrorRecover {
      case ex: DummyException => 99
    }

    assertEquals(coeval.runTry, Success(99))
  }

  test("Coeval#onErrorRecover should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val coeval = Coeval[Int](if (1 == 1) throw ex1 else 1)
      .onErrorRecover { case ex => throw ex2 }

    assertEquals(coeval.runTry, Failure(ex2))
  }

  test("Coeval#onErrorHandle should mirror source on success") { implicit s =>
    val f = Coeval(1).onErrorHandle { case ex: Throwable => 99 }
    assertEquals(f.runTry, Success(1))
  }

  test("Coeval#onErrorHandle should recover") { implicit s =>
    val ex = DummyException("dummy")
    val f = Coeval[Int](if (1 == 1) throw ex else 1).onErrorHandle {
      case ex: DummyException => 99
    }

    assertEquals(f.runTry, Success(99))
  }

  test("Coeval#onErrorHandle should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")
    val f = Coeval[Int](if (1 == 1) throw ex1 else 1)
      .onErrorHandle { case ex => throw ex2 }

    assertEquals(f.runTry, Failure(ex2))
  }

  test("Coeval.onErrorFallbackTo should mirror source onSuccess") { implicit s =>
    val f = Coeval(1).onErrorFallbackTo(Coeval(2))
    assertEquals(f.runTry, Success(1))
  }

  test("Coeval.onErrorFallbackTo should fallback to backup onError") { implicit s =>
    val ex = DummyException("dummy")
    val f = Coeval(throw ex).onErrorFallbackTo(Coeval(2))
    assertEquals(f.runTry, Success(2))
  }

  test("Coeval.onErrorFallbackTo should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val err = DummyException("unexpected")
    val f = Coeval(throw ex).onErrorFallbackTo(Coeval.defer(throw err))
    assertEquals(f.runTry, Failure(err))
  }

  test("Coeval.onErrorRetry should mirror the source onSuccess") { implicit s =>
    var tries = 0
    val f = Coeval.evalAlways { tries += 1; 1 }.onErrorRetry(10)
    assertEquals(f.runTry, Success(1))
    assertEquals(tries, 1)
  }

  test("Coeval.onErrorRetry should retry onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val f = Coeval.evalAlways { tries += 1; if (tries < 5) throw ex else 1 }.onErrorRetry(10)

    assertEquals(f.runTry, Success(1))
    assertEquals(tries, 5)
  }

  test("Coeval.onErrorRetry should emit onError after max retries") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val f = Coeval.evalAlways { tries += 1; throw ex }.onErrorRetry(10)

    assertEquals(f.runTry, Failure(ex))
    assertEquals(tries, 11)
  }

  test("Coeval.onErrorRetryIf should mirror the source onSuccess") { implicit s =>
    var tries = 0
    val f = Coeval.evalAlways { tries += 1; 1 }.onErrorRetryIf(ex => tries < 10)
    assertEquals(f.runTry, Success(1))
    assertEquals(tries, 1)
  }

  test("Coeval.onErrorRetryIf should retry onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val f = Coeval.evalAlways { tries += 1; if (tries < 5) throw ex else 1 }
      .onErrorRetryIf(ex => tries <= 10)

    assertEquals(f.runTry, Success(1))
    assertEquals(tries, 5)
  }

  test("Coeval.onErrorRetryIf should emit onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val f = Coeval.evalAlways { tries += 1; throw ex }
      .onErrorRetryIf(ex => tries <= 10)

    assertEquals(f.runTry, Failure(ex))
    assertEquals(tries, 11)
  }

  test("Coeval#onErrorRecoverWith should mirror source on success") { implicit s =>
    val f = Coeval(1).onErrorRecoverWith { case ex: Throwable => Coeval(99) }
    assertEquals(f.runTry, Success(1))
  }

  test("Coeval#onErrorRecoverWith should recover") { implicit s =>
    val ex = DummyException("dummy")
    val f = Coeval[Int](throw ex).onErrorRecoverWith {
      case ex: DummyException => Coeval(99)
    }

    assertEquals(f.runTry, Success(99))
  }

  test("Coeval#onErrorRecoverWith should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val f = Coeval[Int](throw ex1)
      .onErrorRecoverWith { case ex => throw ex2 }

    assertEquals(f.runTry, Failure(ex2))
  }

  test("Coeval#onErrorRecover should emit error if not matches") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Coeval[Int](throw dummy).onErrorRecover { case _: TimeoutException => 10 }
    assertEquals(f.runTry, Failure(dummy))
  }

  test("Coeval#onErrorRecoverWith should emit error if not matches") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Coeval[Int](throw dummy).onErrorRecoverWith { case _: TimeoutException => Coeval.now(10) }
    assertEquals(f.runTry, Failure(dummy))
  }
}
