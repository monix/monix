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

package monix.reactive.internal.builders

import monix.execution.BaseTestSuite

import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.execution.exceptions.DummyException
import monix.reactive.{ Observable, Observer }
import scala.concurrent.Future
import scala.concurrent.duration._

class FutureAsObservableSuite extends BaseTestSuite {

  fixture.test("should work for synchronous futures and synchronous observers") { implicit s =>
    val f = Future.successful(10)
    var received = 0
    var wasCompleted = false

    Observable
      .fromFuture(f)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = throw ex
        def onComplete(): Unit = {
          assert(!wasCompleted, "wasCompleted should be false")
          wasCompleted = true
        }
      })

    assertEquals(received, 10)
    assert(wasCompleted)
  }

  fixture.test("from should work for asynchronous futures and asynchronous observers") { implicit s =>
    val f = Future.delayedResult(100.millis)(10)
    var received = 0
    var wasCompleted = false

    Observable
      .fromFuture(f)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = {
          received += elem
          Future.delayedResult(100.millis)(Continue)
        }

        def onError(ex: Throwable): Unit = throw ex
        def onComplete(): Unit = {
          assert(!wasCompleted, "wasCompleted should be false")
          wasCompleted = true
        }
      })

    s.tick(100.millis)
    assertEquals(received, 10)
    assert(wasCompleted)
    s.tick(100.millis)
    assert(wasCompleted)
  }

  fixture.test("should emit onError for synchronous futures") { implicit s =>
    val f = Future.failed(DummyException("dummy"))
    var errorThrown: Throwable = null

    Observable
      .fromFuture(f)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = ()
      })

    assertEquals(errorThrown, DummyException("dummy"))
  }

  fixture.test("should emit onError for asynchronous futures") { implicit s =>
    val f = Future.delayedResult(100.millis)(throw DummyException("dummy"))
    var errorThrown: Throwable = null

    Observable
      .fromFuture(f)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = ()
      })

    s.tick(100.millis)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  fixture.test("CancelableFuture should be cancelable") { implicit s =>
    val f = Task.evalAsync(1).delayExecution(1.second).runToFuture
    var received = 0
    var wasCompleted = false

    val cancelable = Observable
      .fromFuture(f)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = { received += 1; Continue }
        def onError(ex: Throwable) = wasCompleted = true
        def onComplete() = wasCompleted = true
      })

    cancelable.cancel()
    s.tick()

    assertEquals(received, 0)
    assert(!wasCompleted)
    assert(s.state.tasks.isEmpty)
  }
}
