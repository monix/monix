/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.operators

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.Observer

import scala.concurrent.Promise

object MiscEndWithErrorSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should end in the specified error") { implicit s =>
    var received = 0
    var wasThrown: Throwable = null
    val p = Promise[Continue]()

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

    s.tick()
    assertEquals(received, 1000)
    assertEquals(wasThrown, null)

    p.success(Continue)
    s.tick()
    assertEquals(wasThrown, DummyException("dummy"))
  }

  test("can end in another unforeseen error") { implicit s =>
    var wasThrown: Throwable = null
    val source = Observable.error(DummyException("unforeseen"))
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
