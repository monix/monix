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

package monix.reactive.consumers

import monix.eval.Callback
import monix.execution.Ack.Stop
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Consumer}

import scala.concurrent.Promise
import scala.util.Failure

object RaiseErrorConsumerSuite extends BaseTestSuite {
  test("cancels and raises error") { implicit s =>
    val ex = DummyException("dummy")
    val consumer = Consumer.raiseError[Int,Unit](ex)

    val p = Promise[Unit]()
    val conn = BooleanCancelable()
    val (out, c) = consumer.createSubscriber(Callback.fromPromise(p), s)
    c := conn

    assertEquals(out.onNext(1), Stop)
    assertEquals(p.future.value, None); s.tick()
    assertEquals(p.future.value, Some(Failure(ex)))
    assert(conn.isCanceled, "conn.isCanceled")
  }

  test("logs onError events") { implicit s =>
    val ex1 = DummyException("dummy1")
    val ex2 = DummyException("dummy2")
    val consumer = Consumer.raiseError(ex1)
    val (out, _) = consumer.createSubscriber(Callback.empty, s)

    out.onError(ex2)
    assertEquals(s.state.lastReportedError, ex2)
    s.tick()
    assertEquals(s.state.lastReportedError, ex1)
  }
}
