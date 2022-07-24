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

import monix.execution.Callback
import monix.execution.Ack.Stop
import monix.execution.Cancelable
import monix.execution.exceptions.DummyException
import monix.reactive.{ Consumer, Observable }
import scala.concurrent.Promise
import scala.util.Success

class CancelConsumerSuite extends BaseTestSuite {

  fixture.test("should cancel immediately") { implicit s =>
    val consumer = Consumer.cancel[Int]

    val p = Promise[Unit]()
    val (out, c) = consumer.createSubscriber(Callback.fromPromise(p), s)

    var wasCancelled = false
    c := Cancelable(() => wasCancelled = true)
    // Cancellation happens immediately
    assert(wasCancelled, "wasCancelled")
    // But the callback is asynchronous
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Success(())))
    assertEquals(out.onNext(1), Stop)
  }

  fixture.test("observable.now") { implicit s =>
    val obs = Observable.now(1)
    val f = obs.consumeWith(Consumer.cancel).runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  fixture.test("onError should report errors") { implicit s =>
    val consumer = Consumer.cancel[Int]
    val (out, _) = consumer.createSubscriber(Callback.empty, s)

    assertEquals(s.state.lastReportedError, null)
    val ex = DummyException("ex")
    out.onError(ex)
    assertEquals(s.state.lastReportedError, ex)
    s.tick()
  }
}
