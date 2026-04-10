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

import cats.effect.IO
import minitest.TestSuite
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.DurationLong

object DoOnStartSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("should work for cats.effect.IO") { implicit s =>
    var wasTriggered = 0
    var wasCompleted = 0

    Observable
      .range(0, 20)
      .doOnStartF(_ => IO { wasTriggered += 1 })
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Continue
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    s.tick()
    assertEquals(wasTriggered, 1)
    assertEquals(wasCompleted, 1)
  }

  test("should work for synchronous subscribers") { implicit s =>
    var wasTriggered = 0
    var wasCompleted = 0

    Observable
      .now(1L)
      .doOnStart(_ => Task.eval { wasTriggered += 1 })
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Continue
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasTriggered, 1)
    assertEquals(wasCompleted, 1)
  }

  test("should work for asynchronous subscribers") { implicit s =>
    var wasTriggered = 0
    var wasCompleted = 0

    Observable
      .now(1L)
      .doOnStart(_ => Task.eval { wasTriggered += 1 })
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = Future(Continue)
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    s.tick()
    assertEquals(wasTriggered, 1)
    assertEquals(wasCompleted, 1)
  }

  test("should stream onError") { implicit s =>
    val dummy = DummyException("ex")
    var wasTriggered = 0
    var wasCompleted = 0
    var errorThrown: Throwable = null

    Observable
      .raiseError[Long](dummy)
      .doOnStart(_ => Task.eval { wasTriggered += 1 })
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long): Future[Ack] =
          if (elem % 2 == 0) Continue else Future(Continue)

        def onError(ex: Throwable): Unit =
          errorThrown = ex
        def onComplete(): Unit =
          wasCompleted += 1
      })

    s.tick()
    assertEquals(wasTriggered, 0)
    assertEquals(wasCompleted, 0)
    assertEquals(errorThrown, dummy)
  }

  test("should be cancelable") { implicit s =>
    var wasTriggered = 0
    val cancelable = Observable
      .now(1)
      .delayOnNext(1.second)
      .doOnStart(_ => Task.eval { wasTriggered += 1 })
      .subscribe()

    s.tick()
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    cancelable.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(wasTriggered, 0)
  }

  test("should protect against user code") { implicit s =>
    val dummy = DummyException("dummy")
    var onNextCalled = 0
    var errorThrown: Throwable = null

    Observable
      .now(1L)
      .doOnStart(_ => throw dummy)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = { onNextCalled += 1; Continue }
        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = ()
      })

    assertEquals(onNextCalled, 0)
    assertEquals(errorThrown, dummy)
  }

  test("should protect against user code for Task") { implicit s =>
    val dummy = DummyException("dummy")
    var onNextCalled = 0
    var errorThrown: Throwable = null

    Observable
      .now(1L)
      .doOnStart(_ => Task.eval { throw dummy })
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long) = { onNextCalled += 1; Continue }
        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = ()
      })

    assertEquals(onNextCalled, 0)
    assertEquals(errorThrown, dummy)
  }
}
