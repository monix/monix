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
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.duration._

object DoOnSubscriptionCancelSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should work") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    val c = Observable.now(1).delaySubscription(1.second)
      .doOnSubscriptionCancel(() => wasCanceled += 1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasCanceled, 0)

    c.cancel()
    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 0)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should protect against user code") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    val c = Observable.now(1).delaySubscription(1.second)
      .doOnSubscriptionCancel(() => wasCanceled += 1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasCanceled, 0)

    c.cancel()
    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 0)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }
}