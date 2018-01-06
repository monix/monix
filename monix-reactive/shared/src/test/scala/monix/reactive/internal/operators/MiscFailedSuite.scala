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

package monix.reactive.internal.operators

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.schedulers.TestScheduler
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer}
import scala.concurrent.Promise

object MiscFailedSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should complete") { implicit s =>
    var received = 0
    var wasCompleted = false

    Observable.now(1).failed.unsafeSubscribeFn(new Observer[Throwable] {
      def onNext(elem: Throwable) = {
        received += 1
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    assertEquals(received, 0)
    assert(wasCompleted)
  }

  test("should signal error without back-pressure applied") { implicit s =>
    var wasCompleted = false
    var thrown: Throwable = null
    val p = Promise[Continue.type]()

    Observable.raiseError(DummyException("dummy")).failed
      .unsafeSubscribeFn(new Observer[Throwable] {
        def onError(ex: Throwable) = ()
        def onComplete() = wasCompleted = true
        def onNext(elem: Throwable) = {
          thrown = elem
          p.future
        }
      })

    assertEquals(thrown, DummyException("dummy"))
    assert(wasCompleted)
    p.success(Continue)
    s.tick()
  }
}
