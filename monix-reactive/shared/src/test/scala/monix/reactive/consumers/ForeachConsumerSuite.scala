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
import scala.util.{ Failure, Success }

class ForeachConsumerSuite extends BaseTestSuite {

  fixture.test("should sum a long stream") { implicit s =>
    val count = 10000L
    val obs = Observable.range(0, count)
    var sum = 0L
    val f = obs.consumeWith(Consumer.foreach(x => sum += x)).runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(sum, count * (count - 1) / 2)
  }

  fixture.test("should interrupt with error") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable.range(0, 10000).endWithError(ex)
    var sum = 0L
    val f = obs.consumeWith(Consumer.foreach(x => sum += x)).runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("should protect against user error") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable
      .now(1)
      .consumeWith(Consumer.foreach(_ => throw ex))
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}
