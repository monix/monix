/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import cats.effect.SyncIO
import minitest.SimpleTestSuite
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success, Try}

object CoevalLikeConversionsSuite extends SimpleTestSuite {
  test("Coeval.from(Coeval)") {
    val source = Coeval(1)
    val conv = Coeval.from(source)
    assertEquals(source, conv)
  }

  test("Coeval.from(Eval)") {
    var effect = false
    val source = Eval.always { effect = true; 1 }
    val conv = Coeval.from(source)

    assert(!effect)
    assertEquals(conv.value(), 1)
    assert(effect)
  }

  test("Coeval.from(Eval) for errors") {
    var effect = false
    val dummy = DummyException("dummy")
    val source = Eval.always[Int] { effect = true; throw dummy }
    val conv = Coeval.from(source)

    assert(!effect)
    assertEquals(conv.runTry(), Failure(dummy))
    assert(effect)
  }

  test("Coeval.from(SyncIO)") {
    var effect = false
    val source = SyncIO { effect = true; 1 }
    val conv = Coeval.from(source)

    assert(!effect)
    assertEquals(conv.value(), 1)
    assert(effect)
  }

  test("Coeval.from(SyncIO) for errors") {
    var effect = false
    val dummy = DummyException("dummy")
    val source = SyncIO.defer[Int] { effect = true; SyncIO.raiseError(dummy) }
    val conv = Coeval.from(source)

    assert(!effect)
    assertEquals(conv.runTry(), Failure(dummy))
    assert(effect)
  }

  test("Coeval.from(Try)") {
    val source = Success(1): Try[Int]
    val conv = Coeval.from(source)
    assertEquals(conv.value(), 1)
  }

  test("Coeval.from(Try) for errors") {
    val dummy = DummyException("dummy")
    val source = Failure(dummy): Try[Int]
    val conv = Coeval.from(source)
    assertEquals(conv.runTry(), Failure(dummy))
  }

  test("Coeval.from(Either)") {
    val source: Either[Throwable, Int] = Right(1)
    val conv = Coeval.from(source)
    assertEquals(conv.value(), 1)
  }

  test("Coeval.from(Either) for errors") {
    val dummy = DummyException("dummy")
    val source: Either[Throwable, Int] = Left(dummy)
    val conv = Coeval.from(source)
    assertEquals(conv.runTry(), Failure(dummy))
  }

  test("Coeval.from(Function0)") {
    val value = Coeval.from(() => 1)
    assertEquals(value.value(), 1)
  }
}
