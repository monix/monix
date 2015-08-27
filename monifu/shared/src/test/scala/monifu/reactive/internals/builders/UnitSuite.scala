/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.builders

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.exceptions.DummyException
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}


object UnitSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()

  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "Scheduler should be left with no pending tasks")
  }

  test("unit should emit one value synchronously") { implicit s =>
    var received = 0
    var completed = false

    Observable.unit(1).onSubscribe(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        received += 1
        Continue
      }

      def onComplete(): Unit = {
        completed = true
      }

      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(received, 1)
    assert(completed)
  }

  test("unit should do back-pressure on onComplete") { implicit s =>
    val p = Promise[Continue]()
    var onCompleteCalled = false
    var received = 0

    Observable.unit(1).onSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = throw ex

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        p.future
      }

      def onComplete() = {
        onCompleteCalled = true
        received += 1
      }
    })

    assert(!onCompleteCalled)
    assertEquals(received, 1)

    p.success(Continue); s.tick()
    assertEquals(received, 2)
  }

  test("unit should not send onComplete if canceled synchronously") { implicit s =>
    Observable.unit(1).onSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = throw ex
      def onNext(elem: Int) = Cancel

      def onComplete() = {
        throw new IllegalStateException("onComplete")
      }
    })
  }

  test("unit should not send onComplete if canceled asynchronously") { implicit s =>
    val p = Promise[Ack]()

    Observable.unit(1).onSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = throw ex
      def onNext(elem: Int) = p.future

      def onComplete() = {
        throw new IllegalStateException("onComplete")
      }
    })

    p.success(Cancel)
    s.tick()

    assert(s.state.get.lastReportedError == null)
  }

  test("unitDelayed should emit") { implicit s =>
    var received = 0
    var completed = false

    Observable.unitDelayed(1.second, 1).onSubscribe(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        received += 1
        Continue
      }

      def onComplete(): Unit = {
        completed = true
      }

      def onError(ex: Throwable): Unit = ()
    })

    s.tick(); assertEquals(received, 0)
    s.tick(1.second)

    assertEquals(received, 1)
    assert(completed)
  }

  test("unitDelayed should do back-pressure on onComplete") { implicit s =>
    val p = Promise[Continue]()
    var onCompleteCalled = false
    var received = 0

    Observable.unitDelayed(1.second, 1).onSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = throw ex

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        p.future
      }

      def onComplete() = {
        onCompleteCalled = true
        received += 1
      }
    })

    s.tick(); assertEquals(received, 0)
    s.tick(1.second)

    assert(!onCompleteCalled)
    assertEquals(received, 1)

    p.success(Continue); s.tick()
    assertEquals(received, 2)
  }

  test("unitDelayed should not send onComplete if canceled synchronously") { implicit s =>
    Observable.unitDelayed(1.second, 1).onSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = throw ex
      def onNext(elem: Int) = Cancel

      def onComplete() = {
        throw new IllegalStateException("onComplete")
      }
    })

    s.tick(1.second)
    assert(s.state.get.lastReportedError == null)
  }

  test("unitDelayed should not send onComplete if canceled asynchronously") { implicit s =>
    val p = Promise[Ack]()

    Observable.unitDelayed(1.second, 1).onSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = throw ex
      def onNext(elem: Int) = p.future

      def onComplete() = {
        throw new IllegalStateException("onComplete")
      }
    })

    s.tick(1.second)
    p.success(Cancel)
    s.tick()

    assert(s.state.get.lastReportedError == null)
  }

  test("empty should complete immediately") { implicit s =>
    var wasCompleted = false
    Observable.empty.onSubscribe(new Observer[Any] {
      def onNext(elem: Any) = throw new IllegalStateException()
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
  }

  test("error should stream immediately") { implicit s =>
    var errorThrown: Throwable = null
    Observable.error(DummyException("dummy")).onSubscribe(new Observer[Any] {
      def onError(ex: Throwable): Unit = errorThrown = ex
      def onNext(elem: Any) = throw new IllegalStateException()
      def onComplete(): Unit = throw new IllegalStateException()
    })

    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("never should never complete") { implicit s =>
    Observable.never.onSubscribe(new Observer[Any] {
      def onNext(elem: Any) = throw new IllegalStateException()
      def onComplete(): Unit = throw new IllegalStateException()
      def onError(ex: Throwable) = new IllegalStateException()
    })

    s.tick(100.days)
    assert(s.state.get.lastReportedError == null)
  }
}
