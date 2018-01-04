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

package monix.reactive.internal.builders

import cats.laws._
import cats.laws.discipline._
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Observable}
import scala.util.Success

object EvalObservableSuite extends BaseTestSuite {
  test("Observable.eval(now(value)) should work") { implicit s =>
    check1 { (value: Int) =>
      val obs1 = Observable.coeval(Coeval.now(value))
      val obs2 = Observable.now(value)
      obs1 <-> obs2
    }
  }

  test("Observable.eval(eval(value)) should work") { implicit s =>
    check1 { (value: Int) =>
      val obs1 = Observable.coeval(Coeval.eval(value))
      val obs2 = Observable.eval(value)
      obs1 <-> obs2
    }
  }

  test("Observable.eval(evalOnce(value)) should work") { implicit s =>
    check1 { (value: Int) =>
      val obs1 = Observable.coeval(Coeval.evalOnce(value))
      val obs2 = Observable.evalOnce(value)
      obs1 <-> obs2
    }
  }

  test("Observable.eval(raiseError(value)) should work") { implicit s =>
    check1 { (value: Int) =>
      val ex = DummyException(s"dummy $value")
      val obs1 = Observable.coeval[Int](Coeval.raiseError(ex))
      val obs2 = Observable.raiseError[Int](ex)
      obs1 <-> obs2
    }
  }

  test("Observable.delay is alias for eval") { implicit s =>
    var effect = 0
    val obs = Observable.delay { effect += 1; effect }
    val f = obs.runAsyncGetFirst

    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }
}
