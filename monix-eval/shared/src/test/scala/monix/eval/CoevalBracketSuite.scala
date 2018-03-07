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

import java.io.{ByteArrayOutputStream, PrintStream}

import cats.laws._
import cats.laws.discipline._
import cats.syntax.all._
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object CoevalBracketSuite extends BaseTestSuite {
  test("equivalence with onErrorHandleWith") { _ =>
    check2 { (coeval: Coeval[Int], f: Throwable => Coeval[Unit]) =>
      val expected = coeval.onErrorHandleWith(e => f(e) *> Coeval.raiseError(e))
      val received = coeval.bracketE(Coeval.now) {
        case (_, Left(e)) => f(e)
        case (_, _) => Coeval.unit
      }
      received <-> expected
    }
  }

  test("equivalence with flatMap + transformWith") { _ =>
    check3 { (acquire: Coeval[Int], f: Int => Coeval[Int], release: Int => Coeval[Unit]) =>
      val expected = acquire.flatMap { a =>
        f(a).transformWith(
          s => release(a) *> Coeval.pure(s),
          e => release(a) *> Coeval.raiseError(e)
        )
      }

      val received = acquire.bracket(f)(release)
      received <-> expected
    }
  }

  test("use is protected against user error") { _ =>
    val dummy = new DummyException("dummy")
    var input = Option.empty[(Int, Either[Throwable, Int])]

    val coeval = Coeval(1).bracketE(_ => throw dummy) { (a, i) =>
      Coeval.eval { input = Some((a, i)) }
    }

    val result = coeval.runTry
    assertEquals(input, Some((1, Left(dummy))))
    assertEquals(result, Failure(dummy))
  }

  test("release is evaluated on success") { _ =>
    var input = Option.empty[(Int, Either[Throwable, Int])]
    val coeval = Coeval(1).bracketE(x => Coeval(x + 1)) { (a, i) =>
      Coeval.eval { input = Some((a, i)) }
    }

    val result = coeval.runTry
    assertEquals(input, Some((1, Right(2))))
    assertEquals(result, Success(2))
  }

  test("release is evaluated on error") { _ =>
    val dummy = new DummyException("dummy")
    var input = Option.empty[(Int, Either[Throwable, Int])]

    val coeval = Coeval(1).bracketE(_ => Coeval.raiseError[Int](dummy)) { (a, i) =>
      Coeval.eval { input = Some((a, i)) }
    }

    val result = coeval.runTry
    assertEquals(input, Some((1, Left(dummy))))
    assertEquals(result, Failure(dummy))
  }

  test("if both use and release throw, report release error, signal use error") { _ =>
    val errRef = System.err
    try {
      val outStream = new ByteArrayOutputStream()
      val fakeErr = new PrintStream(outStream)
      System.setErr(fakeErr)

      val useError = new DummyException("use")
      val releaseError = new DummyException("release")

      val coeval = Coeval(1).bracket[Int] { _ =>
        Coeval.raiseError(useError)
      } { _ =>
        Coeval.raiseError(releaseError)
      }

      assertEquals(coeval.runTry, Failure(useError))
      fakeErr.close()
      val reported = outStream.toString("utf-8")

      assert(reported.contains("DummyException"), "reported.contains(\"DummyException\")")
      assert(reported.contains("release"), "reported.contains(\"release\")")
    } finally {
      System.setErr(errRef)
    }
  }
}
