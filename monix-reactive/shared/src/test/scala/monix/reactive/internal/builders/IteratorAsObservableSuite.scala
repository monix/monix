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

package monix.reactive.internal.builders

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.exceptions.APIContractViolationException
import monix.execution.{Ack, Scheduler}
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

object IteratorAsObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("yields a single subscriber observable") { implicit s =>
    var errorThrown: Throwable = null
    val obs = Observable.fromIterator(Seq(1,2,3).iterator)
    obs.unsafeSubscribeFn(Subscriber.empty(s))

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler = s

      def onNext(elem: Int): Ack =
        throw new IllegalStateException("onNext")
      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        errorThrown = ex
    })

    assert(errorThrown.isInstanceOf[APIContractViolationException])
  }

  test("onFinish should be called upon onComplete") { implicit s =>
    var onFinishCalled = 0
    var onCompleteCalled = 0
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until n
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = { sum += elem; Continue }
      def onComplete(): Unit = onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(sum, n * (n - 1) / 2)
    assertEquals(onCompleteCalled, 1)
    assertEquals(onFinishCalled, 1)
  }

  test("onFinish should be called upon onError") { implicit s =>
    val ex = DummyException("dummy")
    var onFinishCalled = 0
    var onErrorCalled: Throwable = null
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until n
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)
      .endWithError(ex)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack =
        { sum += elem; Continue }
      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        onErrorCalled = ex
    })

    s.tick()
    assertEquals(sum, n * (n - 1) / 2)
    assertEquals(onErrorCalled, ex)
    assertEquals(onFinishCalled, 1)
  }

  test("onFinish should be called upon Stop") { implicit s =>
    var onFinishCalled = 0
    var onCompleteCalled = 0
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until (n * 2)
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)
      .take(n) // Will trigger Stop

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = { sum += elem; Continue }
      def onComplete(): Unit = onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(sum, n * (n - 1) / 2)
    assertEquals(onCompleteCalled, 1)
    assertEquals(onFinishCalled, 1)
  }

  test("onFinish should be called upon subscription cancel") { implicit s =>
    var onFinishCalled = 0
    var onCompleteCalled = 0
    var received = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)

    val c = obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = { received += 1; Continue }
      def onComplete(): Unit = onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    c.cancel()
    s.tick()

    assertEquals(received, n * 2)
    assertEquals(onCompleteCalled, 0)
    assertEquals(onFinishCalled, 1)
  }

  test("onFinish should be called if onNext triggers error before boundary") { implicit s =>
    val ex = DummyException("dummy")
    var onFinishCalled = 0
    var received = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = {
        received += 1
        if (received == n) throw ex
        Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(received, n)
    assertEquals(onFinishCalled, 1)
    assertEquals(s.state.lastReportedError, ex)
  }

  test("onFinish should be called if onNext triggers error after boundary") { implicit s =>
    val ex = DummyException("dummy")
    var onFinishCalled = 0
    var received = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = {
        received += 1
        if (received == n * 2) throw ex
        Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(received, n * 2)
    assertEquals(onFinishCalled, 1)
    assertEquals(s.state.lastReportedError, ex)
  }

  test("onFinish should be called if onNext triggers error asynchronously") { implicit s =>
    val ex = DummyException("dummy")
    var onFinishCalled = 0
    var received = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIterator(seq.iterator, () => onFinishCalled += 1)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        if (received == n * 2)
          Future.failed(ex)
        else
          Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(received, n * 2)
    assertEquals(onFinishCalled, 1)
    assertEquals(s.state.lastReportedError, ex)
  }

  test("onFinish throwing just before onComplete") { implicit s =>
    val ex = DummyException("ex")
    var onErrorCalled: Throwable = null
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until n
    val obs = Observable.fromIterator(seq.iterator, () => throw ex)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = { sum += elem; Continue }
      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        onErrorCalled = ex
    })

    s.tick()
    assertEquals(sum, n * (n - 1) / 2)
    assertEquals(onErrorCalled, ex)
  }

  test("onFinish throwing after Stop") { implicit s =>
    val ex = DummyException("ex")
    var onCompleteCalled = 0
    var received = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIterator(seq.iterator, () => throw ex).take(n)

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Int): Ack = { received += 1; Continue }
      def onComplete(): Unit =
        onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(received, n)
    assertEquals(onCompleteCalled, 1)
    assertEquals(s.state.lastReportedError, ex)
  }
}
