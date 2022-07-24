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

package monix.reactive.internal.builders

import cats.Eval
import cats.effect.IO
import monix.reactive.{ BaseTestSuite, Observable }
import scala.util.Success

class CatsConversionsSuite extends BaseTestSuite {
  fixture.test("from(Eval.now)") { implicit s =>
    val obs = Observable.from(Eval.now(10))
    val f = obs.lastOrElseL(0).runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  fixture.test("from(Eval.always)") { implicit s =>
    val obs = Observable.from(Eval.always(10))
    val f = obs.lastOrElseL(0).runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  fixture.test("fromEffect(IO)") { implicit s =>
    val obs = Observable.fromTaskLike(IO(10))
    val f = obs.lastOrElseL(0).runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  fixture.test("fromIO") { implicit s =>
    val obs = Observable.from(IO(10))
    val f = obs.lastOrElseL(0).runToFuture
    assertEquals(f.value, Some(Success(10)))
  }
}
