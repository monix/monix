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

package monix.reactive.observers

import monix.execution.Ack.Stop
import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Observer}

object StoppedObserverSuite extends BaseTestSuite {
  test("Observer.stopped works") { implicit s =>
    val out = Observer.stopped[Int]

    assertEquals(out.onNext(1), Stop)
    out.onComplete()
    out.onError(DummyException("dummy"))
    assertEquals(out.onNext(2), Stop)
  }

  test("Subscriber.canceled works") { implicit s =>
    val out = Subscriber.canceled[Int]

    assertEquals(out.onNext(1), Stop)
    out.onComplete()
    out.onError(DummyException("dummy"))
    assertEquals(out.onNext(2), Stop)
  }
}