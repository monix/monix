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

import cats.effect.Resource
import minitest.TestSuite
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.exceptions.{ APIContractViolationException, DummyException }
import monix.execution.schedulers.TestScheduler
import monix.execution.{ Ack, ExecutionModel, Scheduler }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

object BufferedIteratorAsObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty, "TestScheduler should be left with no pending tasks")
  }

  test("yields a single subscriber observable") { implicit s =>
    var errorThrown: Throwable = null
    val obs = Observable.fromIteratorBufferedUnsafe(Seq(1, 2, 3).iterator, 1)
    obs.unsafeSubscribeFn(Subscriber.empty(s))

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler = s

      def onNext(elem: Seq[Int]): Ack =
        throw new IllegalStateException("onNext")
      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        errorThrown = ex
    })

    assert(errorThrown.isInstanceOf[APIContractViolationException])
  }

  test("fromIteratorBuffered(resource) should call finalizer") { implicit s =>
    var onFinishCalled = 0
    var onCompleteCalled = 0
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until n
    val obs = Observable
      .fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 4)
      .map { x =>
        assertEquals(onFinishCalled, 0); x
      }

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = { sum += elem.sum; Continue }
      def onComplete(): Unit =
        onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(s.state.lastReportedError, null)
    assertEquals(onCompleteCalled, 1)
    assertEquals(onFinishCalled, 1)
    assertEquals(sum, n * (n - 1) / 2)
  }

  test("fromIteratorBuffered(resource) should back-pressure onNext before calling finalizer") { implicit s =>
    var onFinishCalled = 0
    var onCompleteCalled = 0
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until n
    val obs = Observable
      .fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 4)
      .mapEval(x => Task(assertEquals(onFinishCalled, 0)).map(_ => x).delayExecution(1.millis))

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = { sum += elem.sum; Continue }
      def onComplete(): Unit = onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick(1.millis * n.toLong)
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
    val obs = Observable
      .fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 4)
      .endWithError(ex)

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = { sum += elem.sum; Continue }
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
    val obs = Observable
      .fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 1)
      .take(n.toLong) // Will trigger Stop

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = { sum += elem.sum; Continue }
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
    val obs = Observable.fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 1)

    val c = obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = { received += elem.size; Continue }
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
    var wasThrown: Throwable = null

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 1)

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = {
        received += elem.size
        if (received == n) throw ex
        Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        wasThrown = ex
    })

    s.tick()
    assertEquals(received, n)
    assertEquals(onFinishCalled, 1)
    assertEquals(wasThrown, ex)
  }

  test("onFinish should be called if onNext triggers error after boundary") { implicit s =>
    val ex = DummyException("dummy")
    var onFinishCalled = 0
    var received = 0
    var wasThrown: Throwable = null

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 1)

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = {
        received += elem.size
        if (received == n * 2) throw ex
        Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        wasThrown = ex
    })

    s.tick()
    assertEquals(received, n * 2)
    assertEquals(onFinishCalled, 1)
    assertEquals(wasThrown, ex)
  }

  test("onFinish should be called if onNext triggers error asynchronously") { implicit s =>
    val ex = DummyException("dummy")
    var onFinishCalled = 0
    var received = 0
    var wasThrown: Throwable = null

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable.fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task { onFinishCalled += 1 }), 1)

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Future[Ack] = {
        received += 1
        if (received == n * 2)
          Future.failed(ex)
        else
          Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        wasThrown = ex
    })

    s.tick()
    assertEquals(received, n * 2)
    assertEquals(onFinishCalled, 1)
    assertEquals(wasThrown, ex)
  }

  test("onFinish throwing just before onComplete") { implicit s =>
    val ex = DummyException("ex")
    var wasThrown: Throwable = null
    var sum = 0

    val n = s.executionModel.recommendedBatchSize * 4
    val seq = 0 until n
    val obs = Observable
      .fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task.raiseError(ex)), 1)
      .map { x =>
        assertEquals(wasThrown, null); x
      }

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = {
        sum += elem.sum
        Continue
      }

      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        wasThrown = ex
    })

    s.tick()
    assertEquals(wasThrown, ex)
    assertEquals(sum, n * (n - 1) / 2)
  }

  test("onFinish throwing after Stop") { implicit s =>
    val ex = DummyException("ex")
    var onCompleteCalled = 0
    var received = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until (n * 4)
    val obs = Observable
      .fromIteratorBuffered(Resource.make(Task(seq.iterator))(_ => Task.raiseError(ex)), 1)
      .take(n.toLong)

    obs.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = s

      def onNext(elem: Seq[Int]): Ack = {
        received += elem.size
        Continue
      }

      def onComplete(): Unit =
        onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick()
    assertEquals(received, n)
    // onComplete gets called via `take`, which sends `Stop` upstream and
    // sends onComplete downstream and there isn't much we can do here
    assertEquals(onCompleteCalled, 1)
    assertEquals(s.state.lastReportedError, ex)
  }

  test("fromIteratorBufferedUnsafe insert asynchronous boundaries in BatchedExecution") { s =>
    implicit val sc = s.withExecutionModel(ExecutionModel.Default)

    var onCompleteCalled = 0
    var count = 0

    val n = s.executionModel.recommendedBatchSize
    val seq = 0 until n * 4
    val obs = Observable
      .fromIteratorBufferedUnsafe(seq.iterator, n)
      .delayOnNext(1.millis)

    obs.executeAsync.unsafeSubscribeFn(new Subscriber[Seq[Int]] {
      implicit val scheduler: Scheduler = sc

      def onNext(elem: Seq[Int]): Ack = { count += elem.size; Continue }
      def onComplete(): Unit = onCompleteCalled += 1
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    })

    s.tick(1.millis)
    assertEquals(count, n)

    s.tick(1.millis)
    assertEquals(count, n * 2)

    s.tick(1.millis)
    assertEquals(count, n * 3)

    s.tick(1.millis)
    assertEquals(count, n * 4)

    assertEquals(onCompleteCalled, 1)
  }

  test("emits buffers") { implicit s =>
    import monix.execution.compat.internal.toSeq

    val seq = 0 to 10
    val f = Observable
      .fromIteratorBufferedUnsafe(seq.iterator, 4)
      .toListL
      .runToFuture

    val expected = List(Seq(0, 1, 2, 3), Seq(4, 5, 6, 7), Seq(8, 9, 10)).map(seq =>
      toSeq(seq.map(_.asInstanceOf[AnyRef]).toArray[AnyRef])
    )

    s.tick()

    assertEquals(f.value, Some(Success(expected)))
  }
}
