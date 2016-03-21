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

package monix.reactive.internal.operators

import minitest.TestSuite
import monix.execution.Ack.{Cancel, Continue}
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.exceptions.DummyException
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration._

object DoOnCancelSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should execute for synchronous subscribers") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable.now(1).doOnCancel(wasCanceled += 1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Cancel
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 1)
  }

  test("should execute for asynchronous subscribers") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable.now(1).doOnCancel(wasCanceled += 1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Future(Cancel)
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    s.tick()
    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 1)
  }

  test("should not execute if cancel does not happen") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable.range(0,10).doOnCancel(wasCanceled += 1)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long): Future[Continue] =
          if (elem % 2 == 0) Continue else Future(Continue)

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    s.tick()
    assertEquals(wasCanceled, 0)
    assertEquals(wasCompleted, 1)
  }

  test("should stream onError") { implicit s =>
    val dummy = DummyException("ex")
    var wasCanceled = 0
    var wasCompleted = 0
    var errorThrown: Throwable = null

    Observable.error(dummy).doOnCancel(wasCanceled += 1)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long): Future[Continue] =
          if (elem % 2 == 0) Continue else Future(Continue)

        def onError(ex: Throwable): Unit =
          errorThrown = ex
        def onComplete(): Unit =
          wasCompleted += 1
      })

    s.tick()
    assertEquals(wasCanceled, 0)
    assertEquals(wasCompleted, 0)
    assertEquals(errorThrown, dummy)
  }

  test("should be cancelable from outside") { implicit s =>
    var wasCanceled = 0
    val cancelable = Observable.now(1).delayOnNext(1.second)
      .doOnCancel(wasCanceled += 1)
      .subscribe()

    s.tick()
    assert(s.state.get.tasks.nonEmpty, "tasks.nonEmpty")

    cancelable.cancel()
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(wasCanceled, 1)
  }

  test("should protect against user code") { implicit s =>
    val dummy = DummyException("dummy")
    var hasError = false

    Observable.now(1).doOnCancel(throw dummy)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s

        def onNext(elem: Int) = Cancel
        def onError(ex: Throwable) = hasError = true
        def onComplete() = ()
      })

    s.tick()
    assertEquals(s.state.get.lastReportedError, dummy)
    assertEquals(hasError, false)
  }
}