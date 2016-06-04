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
import monix.execution.Ack.{Continue, Stop}
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.exceptions.DummyException
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

object DoOnTerminateSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should execute callback onComplete") { implicit s =>
    var wasCalled = 0
    var wasCompleted = 0

    Observable.now(1)
      .doOnTerminate(wasCalled += 1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasCalled, 1)
    assertEquals(wasCompleted, 1)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should protect against user code onComplete") { implicit s =>
    val ex = DummyException("dummy")
    var wasThrown: Throwable = null

    Observable.now(1)
      .doOnTerminate(throw ex)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit =
          wasThrown = ex
      })

    assertEquals(wasThrown, ex)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should execute callback onError") { implicit s =>
    val ex = DummyException("dummy")
    var wasCalled = 0
    var wasThrown: Throwable = null

    Observable.now(1).endWithError(ex)
      .doOnTerminate(wasCalled += 1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit =
          wasThrown = ex
      })

    assertEquals(wasCalled, 1)
    assertEquals(wasThrown, ex)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should protect against user-code onError") { implicit s =>
    val ex1 = DummyException("dummy1")
    val ex2 = DummyException("dummy2")
    var wasThrown: Throwable = null

    Observable.now(1).endWithError(ex1)
      .doOnTerminate(throw ex2)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit =
          wasThrown = ex
      })

    assertEquals(wasThrown, ex1)
    assertEquals(s.state.get.lastReportedError, ex2)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should call on synchronous downstream Stop") { implicit s =>
    var wasCalled = 0
    var wasCompleted = 0

    Observable.range(0, 100)
      .doOnTerminate(wasCalled += 1)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Stop
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted = 1
      })

    assertEquals(wasCalled, 1)
    assertEquals(wasCompleted, 0)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should call on asynchronous downstream Stop") { implicit s =>
    var wasCalled = 0
    var wasCompleted = 0

    Observable.range(0, 100)
      .doOnTerminate(wasCalled += 1)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Future(Stop)
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted = 1
      })

    s.tick()
    assertEquals(wasCalled, 1)
    assertEquals(wasCompleted, 0)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should protect against user code on synchronous downstream Stop") { implicit s =>
    val ex = DummyException("dummy")

    Observable.range(0, 100)
      .doOnTerminate(throw ex)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Stop
        def onError(ex: Throwable): Unit =
          throw new IllegalStateException("onError")
        def onComplete(): Unit =
          throw new IllegalStateException("onComplete")
      })

    assertEquals(s.state.get.lastReportedError, ex)
  }

  test("should protect against user code on asynchronous downstream Stop") { implicit s =>
    val ex = DummyException("dummy")

    Observable.range(0, 100)
      .doOnTerminate(throw ex)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Future(Stop)
        def onError(ex: Throwable): Unit =
          throw new IllegalStateException("onError")
        def onComplete(): Unit =
          throw new IllegalStateException("onComplete")
      })

    s.tick()
    assertEquals(s.state.get.lastReportedError, ex)
  }
}