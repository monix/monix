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
import monix.reactive.{Observable, Observer}
import monix.execution.exceptions.DummyException

object DoOnSubscribeSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("doOnSubscribe should work") { implicit s =>
    var elem = 0
    Observable
      .now(10)
      .doOnSubscribe { () => elem = 20 }
      .foreach { x => elem = elem / x }

    s.tick()
    assertEquals(elem, 2)
  }

  test("doOnSubscribe should protect against error") { implicit s =>
    val dummy = DummyException("dummy")
    var wasThrown: Throwable = null
    Observable
      .range(1,10)
      .doOnSubscribe { () => throw dummy }
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = Continue
        def onComplete() = ()
        def onError(ex: Throwable) = wasThrown = ex
      })

    s.tick()
    assertEquals(wasThrown, dummy)
  }

  test("doAfterSubscribe should work") { implicit s =>
    var elem = 0
    Observable
      .now(10)
      .doAfterSubscribe { () => elem = 20 }
      .foreach { x => elem = elem / x }

    s.tick()
    assertEquals(elem, 20)
  }

  test("doAfterSubscribe should protect against error") { implicit s =>
    val dummy = DummyException("dummy")
    var wasThrown: Throwable = null
    Observable
      .range(1,10)
      .doAfterSubscribe { () => throw dummy }
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = Continue
        def onComplete() = ()
        def onError(ex: Throwable) = wasThrown = ex
      })

    s.tick()
    assertEquals(wasThrown, null)
    assertEquals(s.state.lastReportedError, dummy)
  }
}
