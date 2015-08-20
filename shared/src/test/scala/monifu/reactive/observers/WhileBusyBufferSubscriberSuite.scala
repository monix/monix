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

package monifu.reactive.observers

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{DummyException, Observer, Ack}
import scala.concurrent.{Future, Promise}
import scala.util.Success


object WhileBusyBufferSubscriberSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should do back-pressure") { implicit s =>
    val promise = Promise[Ack]()
    var sum = 0
    var wasCompleted = false

    val observer = new Observer[Seq[Int]] {
      def onNext(elem: Seq[Int]) = {
        sum += elem.sum
        promise.future
      }

      def onError(ex: Throwable) =
        throw new IllegalStateException()

      def onComplete() =
        wasCompleted = true
    }

    val buffer = WhileBusyBufferSubscriber(observer, 5)

    assertEquals(buffer.observer.onNext(1), Continue)
    assertEquals(buffer.observer.onNext(2), Continue)
    assertEquals(buffer.observer.onNext(3), Continue)
    assertEquals(buffer.observer.onNext(4), Continue)
    assertEquals(buffer.observer.onNext(5), Continue)

    val async = buffer.observer.onNext(6)
    assert(async.value != Some(Success(Continue)))

    promise.success(Continue)
    s.tick()

    assertEquals(buffer.observer.onNext(1), Continue)
    assertEquals(buffer.observer.onNext(2), Continue)
    assertEquals(buffer.observer.onNext(3), Continue)
    assertEquals(buffer.observer.onNext(4), Continue)
    assertEquals(buffer.observer.onNext(5), Continue)

    s.tick()
    assert(!wasCompleted)

    buffer.observer.onComplete()
    s.tick()
    assert(wasCompleted)
    assertEquals(sum, 36)
  }

  test("should not lose events, test 1") { implicit s =>
    var number = 0
    var wasCompleted = false

    val underlying = new Observer[Seq[Int]] {
      def onNext(elem: Seq[Int]): Future[Ack] = {
        number += elem.size
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        wasCompleted = true
      }
    }

    val buffer = WhileBusyBufferSubscriber(underlying, 1000)
    for (i <- 0 until 1000) buffer.observer.onNext(i)
    buffer.observer.onComplete()

    assert(!wasCompleted)
    s.tick()
    assert(number == 1000)
    assert(wasCompleted)
  }

  test("should not lose events, test 2") { implicit s =>
    var number = 0
    var completed = false

    val underlying = new Observer[Seq[Int]] {
      def onNext(elem: Seq[Int]): Future[Ack] = {
        number += elem.length
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    val buffer = WhileBusyBufferSubscriber(underlying, 1000)

    def loop(n: Int): Unit =
      if (n > 0)
        s.execute { buffer.observer.onNext(n); loop(n-1) }
      else
        buffer.observer.onComplete()

    loop(10000)
    assert(!completed)
    assertEquals(number, 0)

    s.tick()
    assert(completed)
    assertEquals(number, 10000)
  }

  test("should send onError when empty") { implicit s =>
    var errorThrown: Throwable = null
    val observer = new Observer[Seq[Int]] {
      def onError(ex: Throwable) = {
        errorThrown = ex
      }

      def onNext(elem: Seq[Int]) =
        throw new IllegalStateException()

      def onComplete() =
        throw new IllegalStateException()
    }

    val buffer = WhileBusyBufferSubscriber(observer, 5)

    buffer.observer.onError(DummyException("dummy"))
    s.tickOne()

    assertEquals(errorThrown, DummyException("dummy"))
    val r = buffer.observer.onNext(1)
    assertEquals(r, Cancel)
  }

  test("should send onError when in flight") { implicit s =>
    var errorThrown: Throwable = null

    val observer = new Observer[Seq[Int]] {
      def onNext(elem: Seq[Int]) = Continue
      def onError(ex: Throwable) = {
        errorThrown = ex
      }

      def onComplete() =
        throw new IllegalStateException()
    }

    val buffer = WhileBusyBufferSubscriber(observer, 5)

    buffer.observer.onNext(1)
    buffer.observer.onError(DummyException("dummy"))
    s.tickOne()

    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should send onError when at capacity") { implicit s =>
    var errorThrown: Throwable = null
    val promise = Promise[Ack]()

    val observer = new Observer[Seq[Int]] {
      def onNext(elem: Seq[Int]) = promise.future
      def onError(ex: Throwable) = {
        errorThrown = ex
      }
      def onComplete() =
        throw new IllegalStateException()
    }

    val buffer = WhileBusyBufferSubscriber(observer, 5)

    buffer.observer.onNext(1)
    buffer.observer.onNext(2)
    buffer.observer.onNext(3)
    buffer.observer.onNext(4)
    buffer.observer.onNext(5)
    buffer.observer.onError(DummyException("dummy"))

    promise.success(Continue)
    s.tick()

    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should send onComplete when empty") { implicit s =>
    var wasCompleted = false
    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Int]] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Seq[Int]) = throw new IllegalStateException()
      def onComplete() = wasCompleted = true
    }, 5)

    buffer.observer.onComplete()
    s.tickOne()
    assert(wasCompleted)
  }

  test("should send onComplete when in flight") { implicit s =>
    var wasCompleted = false
    val promise = Promise[Ack]()
    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Int]] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Seq[Int]) = promise.future
      def onComplete() = wasCompleted = true
    }, 5)

    buffer.observer.onNext(1)
    buffer.observer.onComplete()
    s.tick()
    assert(!wasCompleted)

    promise.success(Continue)
    s.tick()
    assert(wasCompleted)
  }

  test("should send onComplete when at capacity") { implicit s =>
    var wasCompleted = false
    val promise = Promise[Ack]()

    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Int]] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Seq[Int]) = promise.future
      def onComplete() = wasCompleted = true
    }, 5)

    buffer.observer.onNext(1)
    buffer.observer.onNext(2)
    buffer.observer.onNext(3)
    buffer.observer.onNext(4)
    buffer.observer.onComplete()

    s.tick()
    assert(!wasCompleted)

    promise.success(Continue)
    s.tick()
    assert(wasCompleted)
  }

  test("should do onComplete only after all the queue was drained") { implicit s =>
    var sum = 0L
    var wasCompleted = false
    val startConsuming = Promise[Continue]()

    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Long]] {
      def onNext(elem: Seq[Long]) = {
        sum += elem.sum
        startConsuming.future
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = wasCompleted = true
    }, 10000)

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onComplete()
    startConsuming.success(Continue)

    s.tick()
    assert(wasCompleted)
    assert(sum == (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    var wasCompleted = false

    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Long]] {
      def onNext(elem: Seq[Long]) = {
        sum += elem.sum
        Continue
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = wasCompleted = true
    }, 10000)

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onComplete()
    s.tick()

    assert(wasCompleted)
    assert(sum == (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    var errorThrown: Throwable = null
    val startConsuming = Promise[Continue]()

    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Long]] {
      def onNext(elem: Seq[Long]) = {
        sum += elem.sum
        startConsuming.future
      }
      def onError(ex: Throwable) = errorThrown = ex
      def onComplete() = throw new IllegalStateException()
    }, 10000)

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onError(DummyException("dummy"))
    startConsuming.success(Continue)

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    var errorThrown: Throwable = null

    val buffer = WhileBusyBufferSubscriber(new Observer[Seq[Long]] {
      def onNext(elem: Seq[Long]) = {
        sum += elem.sum
        Continue
      }
      def onError(ex: Throwable) = errorThrown = ex
      def onComplete() = throw new IllegalStateException()
    }, 10000)

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onError(DummyException("dummy"))

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
    assertEquals(sum, (0 until 9999).sum)
  }
}
