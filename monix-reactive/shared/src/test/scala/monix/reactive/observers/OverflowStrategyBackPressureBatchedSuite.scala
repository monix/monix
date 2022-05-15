/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.reactive.observers

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.ChannelType.MultiProducer
import monix.execution.schedulers.TestScheduler
import monix.execution.exceptions.DummyException

import scala.concurrent.{Future, Promise}
import scala.util.Success

object OverflowStrategyBackPressureBatchedSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("should do back-pressure") { implicit s =>
    val promise = Promise[Ack]()
    var wasCompleted = false

    val buffer = BufferedSubscriber.batched[Int](
      bufferSize = 5,
      producerType = MultiProducer,
      underlying = new Subscriber[List[Int]] {
        def onNext(elem: List[Int]) = promise.future
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onComplete() = wasCompleted = true
        val scheduler = s
      }
    )

    assertEquals(buffer.onNext(1), Continue)
    assertEquals(buffer.onNext(2), Continue)
    assertEquals(buffer.onNext(3), Continue)
    assertEquals(buffer.onNext(4), Continue)
    assertEquals(buffer.onNext(5), Continue)
    assertEquals(buffer.onNext(6), Continue)
    assertEquals(buffer.onNext(7), Continue)
    assertEquals(buffer.onNext(8), Continue)
    buffer.onNext(9) // uncertain

    val async = buffer.onNext(10)
    assertEquals(async.value, None)
    promise.success(Continue)

    s.tick()
    assertEquals(async.value, Some(Success(Continue)))

    assertEquals(buffer.onNext(1), Continue)
    assertEquals(buffer.onNext(2), Continue)
    assertEquals(buffer.onNext(3), Continue)
    assertEquals(buffer.onNext(4), Continue)
    assertEquals(buffer.onNext(5), Continue)

    s.tick()
    assert(!wasCompleted)

    buffer.onComplete()
    s.tick()
    assert(wasCompleted)
  }

  test("should not lose events, test 1") { implicit s =>
    var sum = 0
    var wasCompleted = false

    val underlying = new Subscriber[List[Int]] {
      val scheduler = s

      def onNext(list: List[Int]): Future[Ack] = {
        sum += list.sum
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        wasCompleted = true
      }
    }

    val buffer = BufferedSubscriber.batched[Int](underlying, 1000, MultiProducer)
    for (i <- 0 until 1000) buffer.onNext(i)
    buffer.onComplete()

    assert(!wasCompleted)
    s.tick()
    assert(sum == 999 * 500)
    assert(wasCompleted)
  }

  test("should not lose events, test 2") { implicit s =>
    var sum = 0
    var completed = false

    val underlying = new Subscriber[List[Int]] {
      val scheduler = s

      def onNext(list: List[Int]): Future[Ack] = {
        sum += list.sum
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    val buffer = BufferedSubscriber.batched[Int](underlying, 1000, MultiProducer)
    def loop(n: Int): Unit =
      if (n > 0)
        s.execute { () =>
          buffer.onNext(n); loop(n - 1)
        }
      else
        buffer.onComplete()

    loop(10000)
    assert(!completed)
    assertEquals(sum, 0)

    s.tick()
    assert(completed)
    assertEquals(sum, 10001 * 5000)
  }

  test("should not lose events, test 3") { implicit s =>
    var sum = 0
    var completed = false

    val underlying = new Subscriber[List[Int]] {
      val scheduler = s

      def onNext(list: List[Int]): Future[Ack] = {
        sum += list.sum
        Future(Continue)
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    val buffer = BufferedSubscriber.batched[Int](underlying, 512, MultiProducer)
    def loop(n: Int): Unit =
      if (n > 0)
        s.execute { () =>
          buffer.onNext(n); loop(n - 1)
        }
      else
        buffer.onComplete()

    loop(10000)
    assert(!completed)
    assertEquals(sum, 0)

    s.tick()
    assert(completed)
    assertEquals(sum, 10001 * 5000)
  }

  test("should send onError when empty") { implicit s =>
    var errorThrown: Throwable = null
    val underlying = new Subscriber[List[Int]] {
      def onError(ex: Throwable) = errorThrown = ex
      def onNext(elem: List[Int]) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }

    val buffer = BufferedSubscriber.batched(underlying, 5, MultiProducer)
    buffer.onError(DummyException("dummy"))
    s.tickOne()

    assertEquals(errorThrown, DummyException("dummy"))
    val r = buffer.onNext(1)
    assertEquals(r, Stop)
  }

  test("should send onError when in flight") { implicit s =>
    var errorThrown: Throwable = null
    val promise = Promise[Ack]()
    val underlying = new Subscriber[List[Int]] {
      def onError(ex: Throwable) = errorThrown = ex
      def onNext(elem: List[Int]) = promise.future
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }

    val buffer = BufferedSubscriber.batched(underlying, 5, MultiProducer)
    buffer.onNext(1)
    buffer.onError(DummyException("dummy"))

    s.tickOne()
    assertEquals(errorThrown, DummyException("dummy"))
    promise.success(Continue); ()
  }

  test("should send onError when at capacity") { implicit s =>
    var errorThrown: Throwable = null
    val promise = Promise[Ack]()
    val underlying = new Subscriber[List[Int]] {
      def onError(ex: Throwable) = errorThrown = ex
      def onNext(elem: List[Int]) = promise.future
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }

    val buffer = BufferedSubscriber.batched(underlying, 5, MultiProducer)
    for (i <- 0 until 20) buffer.onNext(i)
    buffer.onError(DummyException("dummy"))

    promise.success(Continue); s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should send onComplete when empty") { implicit s =>
    var wasCompleted = false
    val underlying = new Subscriber[List[Int]] {
      val scheduler = s
      def onNext(elem: List[Int]) = throw new IllegalStateException("onNext")
      def onError(ex: Throwable) = throw ex
      def onComplete() = wasCompleted = true
    }

    val buffer = BufferedSubscriber.batched(underlying, 8, MultiProducer)
    buffer.onComplete()

    s.tickOne()
    assert(wasCompleted)
  }

  test("should not back-pressure onComplete") { implicit s =>
    var wasCompleted = false
    val promise = Promise[Ack]()

    val underlying = new Subscriber[List[Int]] {
      val scheduler = s
      def onNext(elem: List[Int]) = promise.future
      def onError(ex: Throwable) = throw ex
      def onComplete() = wasCompleted = true
    }

    val buffer = BufferedSubscriber.batched(underlying, 8, MultiProducer)
    buffer.onNext(1)
    buffer.onComplete()

    s.tick(); assert(wasCompleted)
    promise.success(Continue)

    s.tick()
    assert(wasCompleted)
  }

  test("should signal Stop upstream when it is back-pressured") { implicit s =>
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber.batched[Int](
      bufferSize = 2,
      producerType = MultiProducer,
      underlying = new Subscriber[List[Int]] {
        def onNext(elem: List[Int]) = promise.future
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onComplete() = ()
        val scheduler = s
      }
    )

    assertEquals(buffer.onNext(1), Continue)
    assertEquals(buffer.onNext(2), Continue)
    s.tick()
    assertEquals(buffer.onNext(3), Continue)
    assertEquals(buffer.onNext(4), Continue)

    val async = buffer.onNext(5)
    assertEquals(async.value, None)
    promise.success(Stop)

    s.tick()
    assertEquals(async.value, Some(Success(Stop)))
  }
}
