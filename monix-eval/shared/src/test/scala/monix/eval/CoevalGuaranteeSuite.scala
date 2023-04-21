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

import monix.execution.exceptions.{ CompositeException, DummyException }
import monix.execution.internal.Platform
import scala.util.{ Failure, Success }

object CoevalGuaranteeSuite extends BaseTestSuite {

  test("finalizer is evaluated on success") { _ =>
    var input = Option.empty[Int]
    val coeval = Coeval(1).map(_ + 1).guarantee(Coeval.eval { input = Some(1) })

    val result = coeval.runTry()
    assertEquals(input, Some(1))
    assertEquals(result, Success(2))
  }

  test("finalizer is evaluated on error") { _ =>
    val dummy = DummyException("dummy")
    var input = Option.empty[Int]
    val coeval = Coeval.raiseError[Int](dummy).guarantee(Coeval.eval { input = Some(1) })

    val result = coeval.runTry()
    assertEquals(input, Some(1))
    assertEquals(result, Failure(dummy))
  }

  test("if finalizer throws, report finalizer error and signal first error") { _ =>
    val useError = DummyException("dummy")
    val finalizerError = DummyException("finalizer")

    val coeval = Coeval(1)
      .flatMap(_ => Coeval.raiseError[Int](useError))
      .guarantee(Coeval.raiseError[Unit](finalizerError))

    coeval.runTry() match {
      case Failure(error) =>
        if (Platform.isJVM) {
          assertEquals(error, useError)
          error.getSuppressed match {
            case Array(error2) =>
              assertEquals(error2, finalizerError)
            case _ =>
              fail("Unexpected suppressed errors list: " + error.getSuppressed.toList)
          }
        } else
          error match {
            case CompositeException(Seq(`useError`, `finalizerError`)) =>
              () // pass
            case _ =>
              fail(s"Unexpected error: $error")
          }

      case other =>
        fail(s"Unexpected result: $other")
    }
  }
}
