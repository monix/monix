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

object EndWithErrorSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should end in the specified error") { implicit s =>
    var received = 0
    var wasThrown: Throwable = null
    val p = Promise[Continue.type]()

    val source = Observable.now(1000)
      .endWithError(DummyException("dummy"))

    source.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = {
        received = elem
        p.future
      }

      def onComplete() = ()
      def onError(ex: Throwable) = {
        wasThrown = ex
      }
    })

    assertEquals(received, 1000)
    assertEquals(wasThrown, DummyException("dummy"))
    p.success(Continue)
    s.tick()
  }

  test("can end in another unforeseen error") { implicit s =>
    var wasThrown: Throwable = null
    val source = Observable.raiseError(DummyException("unforeseen"))
      .endWithError(DummyException("expected"))

    source.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = Continue
      def onComplete() = ()
      def onError(ex: Throwable) = {
        wasThrown = ex
      }
    })

    assertEquals(wasThrown, DummyException("unforeseen"))
  }
}
