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

import cats.Eval
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException

import scala.util.Failure

object CoevalCatsConversions extends BaseTestSuite {
  test("Coeval.now(value).toEval") { _ =>
    assertEquals(Coeval.now(10).toEval.value, 10)
  }

  test("Coeval.raiseError(e).toEval") { _ =>
    val dummy = DummyException("dummy")
    val eval = Coeval.raiseError(dummy).toEval
    intercept[DummyException] { eval.value }
  }

  test("Coeval.eval(thunk).toEval") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.eval(effect.incrementAndGet()).toEval

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 2)
  }

  test("Coeval.evalOnce(thunk).toEval") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.evalOnce(effect.incrementAndGet()).toEval

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 1)
  }

  test("Coeval.now(value).toIO") { _ =>
    assertEquals(Coeval.now(10).toIO.unsafeRunSync(), 10)
  }

  test("Coeval.raiseError(e).toIO") { _ =>
    val dummy = DummyException("dummy")
    val ioRef = Coeval.raiseError(dummy).toIO
    intercept[DummyException] { ioRef.unsafeRunSync() }
  }

  test("Coeval.eval(thunk).toIO") { _ =>
    val effect = Atomic(0)
    val ioRef = Coeval.eval(effect.incrementAndGet()).toIO

    assertEquals(ioRef.unsafeRunSync(), 1)
    assertEquals(ioRef.unsafeRunSync(), 2)
  }

  test("Coeval.evalOnce(thunk).toIO") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.evalOnce(effect.incrementAndGet()).toIO

    assertEquals(eval.unsafeRunSync(), 1)
    assertEquals(eval.unsafeRunSync(), 1)
  }

  test("Coeval.fromEval(Eval.now(v))") { _ =>
    assertEquals(Coeval.fromEval(Eval.now(10)), Coeval.Now(10))
  }

  test("Coeval.fromEval(Eval.always(v))") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.fromEval(Eval.always(effect.incrementAndGet()))

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 2)
    assertEquals(eval.value, 3)
  }

  test("Coeval.fromEval(Eval.later(v))") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.fromEval(Eval.later(effect.incrementAndGet()))

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 1)
  }

  test("Coeval.fromEval protects against user error") { implicit s =>
    val dummy = new DummyException("dummy")
    val eval = Coeval.fromEval(Eval.always { throw dummy })
    assertEquals(eval.runTry, Failure(dummy))
  }
}
