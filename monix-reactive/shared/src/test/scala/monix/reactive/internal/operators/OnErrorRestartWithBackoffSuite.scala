/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Observable}

import scala.concurrent.duration._
import scala.util.Success

object OnErrorRestartWithBackoffSuite extends BaseTestSuite {
  test("Observable.onErrorRestartWithBackoff resets the delay after success") { implicit s =>
    val dummy = DummyException("dummy")

    val source = Observable
      .range(0, 100)
      .flatMap(n => if (n == 2) Observable.raiseError(dummy) else Observable.now(n))
      .onErrorRestartWithBackoff(10, 1.second)
      .takeByTimespan(5.second)
      .toListL

    val f = source.runToFuture

    s.tick(5.seconds)
    assertEquals(f.value, Some(Success(List(0, 1, 0, 1, 0, 1, 0, 1, 0, 1))))
  }
}