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

package monix.reactive.consumers

import monix.execution.BaseTestSuite

import monix.execution.exceptions.DummyException
import monix.reactive.{ Consumer, Observable }

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class CompleteConsumerSuite extends BaseTestSuite {

  fixture.test("should run to completion") { implicit s =>
    val obs = Observable(1) ++ Observable.now(2).delayExecution(3.seconds)
    val f = obs.consumeWith(Consumer.complete).runToFuture

    s.tick(); assertEquals(f.value, None)
    s.tick(3.seconds); assertEquals(f.value, Some(Success(())))
  }

  fixture.test("should trigger error") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable.raiseError(ex).consumeWith(Consumer.complete).runToFuture
    s.tick(); assertEquals(f.value, Some(Failure(ex)))
  }
}
