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

package monix.reactive.internal.operators

import monix.execution.BaseTestSuite

import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.{ Observable, Observer }

class MiscCompleteSuite extends BaseTestSuite {

  fixture.test("should complete") { implicit s =>
    var received = 0
    var wasCompleted = false

    Observable
      .now(1)
      .ignoreElements
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          received += 1
          Continue
        }

        def onError(ex: Throwable) = ()
        def onComplete() = wasCompleted = true
      })

    assertEquals(received, 0)
    assert(wasCompleted)
  }

  fixture.test("should signal error") { implicit s =>
    var thrown: Throwable = null

    Observable
      .raiseError(DummyException("dummy"))
      .ignoreElements
      .unsafeSubscribeFn(new Observer[Long] {
        def onError(ex: Throwable) = thrown = ex
        def onComplete() = ()
        def onNext(elem: Long) = Continue
      })

    assertEquals(thrown, DummyException("dummy"))
  }
}
