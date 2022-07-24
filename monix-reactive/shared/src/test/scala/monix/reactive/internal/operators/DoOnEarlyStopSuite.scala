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
import monix.execution.BaseTestSuite

import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

class DoOnEarlyStopSuite extends BaseTestSuite {

  fixture.test("should execute for cats.effect.IO") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable
      .now(1)
      .doOnEarlyStopF(IO { wasCanceled += 1 })
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Stop
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 1)
  }

  fixture.test("should execute for synchronous subscribers") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable
      .now(1)
      .doOnEarlyStop(Task.eval { wasCanceled += 1 })
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Stop
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 1)
  }

  fixture.test("should execute for asynchronous subscribers") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable
      .now(1)
      .doOnEarlyStop(Task.evalAsync { wasCanceled += 1 })
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Future(Stop)
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    s.tick()
    assertEquals(wasCanceled, 1)
    assertEquals(wasCompleted, 1)
  }

  fixture.test("should not execute if cancel does not happen") { implicit s =>
    var wasCanceled = 0
    var wasCompleted = 0

    Observable
      .range(0, 10)
      .doOnEarlyStop(Task.eval { wasCanceled += 1 })
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long): Future[Ack] =
          if (elem % 2 == 0) Continue else Future(Continue)

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    s.tick()
    assertEquals(wasCanceled, 0)
    assertEquals(wasCompleted, 1)
  }

  fixture.test("should stream onError") { implicit s =>
    val dummy = DummyException("ex")
    var wasCanceled = 0
    var wasCompleted = 0
    var errorThrown: Throwable = null

    Observable
      .raiseError(dummy)
      .doOnEarlyStop(Task.eval { wasCanceled += 1 })
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
    assertEquals(wasCanceled, 0)
    assertEquals(wasCompleted, 0)
    assertEquals(errorThrown, dummy)
  }

  fixture.test("should protect against user code") { implicit s =>
    val dummy = DummyException("dummy")
    var hasError = false

    Observable
      .repeat(1)
      .doOnEarlyStop(Task.eval { throw dummy })
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Stop
        def onError(ex: Throwable) = hasError = true
        def onComplete() = ()
      })

    s.tick()
    assertEquals(s.state.lastReportedError, dummy)
    assertEquals(hasError, false)
  }
}
