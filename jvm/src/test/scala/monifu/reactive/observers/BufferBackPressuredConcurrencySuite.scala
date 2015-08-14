/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

import java.util.concurrent.{CountDownLatch, TimeUnit}

import minitest.TestSuite
import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.BackPressured
import monifu.reactive.{Ack, DummyException, Observer}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}


object BufferBackPressuredConcurrencySuite extends TestSuite[Scheduler] {
  def tearDown(env: Scheduler) = ()
  def setup() = {
    monifu.concurrent.Implicits.globalScheduler
  }

  test("should do back-pressure") { implicit s =>
    val promise = Promise[Ack]()
    val completed = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onNext(elem: Int) = promise.future
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onComplete() = completed.countDown()
    }, BackPressured(5))

    assertEquals(buffer.observer.onNext(1), Continue)
    assertEquals(buffer.observer.onNext(2), Continue)
    assertEquals(buffer.observer.onNext(3), Continue)
    assertEquals(buffer.observer.onNext(4), Continue)
    assertEquals(buffer.observer.onNext(5), Continue)

    val async = buffer.observer.onNext(6)
    assert(async != Continue)

    promise.success(Continue)
    Await.result(async, 10.seconds)

    assertEquals(buffer.observer.onNext(1), Continue)
    assertEquals(buffer.observer.onNext(2), Continue)
    assertEquals(buffer.observer.onNext(3), Continue)
    assertEquals(buffer.observer.onNext(4), Continue)
    assertEquals(buffer.observer.onNext(5), Continue)
    assert(!completed.await(100, TimeUnit.MILLISECONDS), "completed.await shouldn't have succeeded")

    buffer.observer.onComplete()
    assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
  }

  test("should not lose events, test 1") { implicit s =>
    var number = 0
    val completed = new CountDownLatch(1)

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed.countDown()
      }
    }

    val buffer = BufferedSubscriber[Int](underlying, BackPressured(100000))
    for (i <- 0 until 100000) buffer.observer.onNext(i)
    buffer.observer.onComplete()

    assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    assert(number == 100000)
  }

  test("should not lose events, test 2") { implicit s =>
    var number = 0
    val completed = new CountDownLatch(1)

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed.countDown()
      }
    }

    val buffer = BufferedSubscriber[Int](underlying, BackPressured(100000))

    def loop(n: Int): Unit =
      if (n > 0) s.execute(new Runnable {
        def run() = { buffer.observer.onNext(n); loop(n-1) }
      })
      else buffer.observer.onComplete()

    loop(10000)
    assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    assertEquals(number, 10000)
  }

  test("should send onError when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }

      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
    }, BackPressured(5))

    buffer.observer.onError(new RuntimeException("dummy"))
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")

    val r = buffer.observer.onNext(1)
    assertEquals(r, Cancel)
  }

  test("should send onError when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = Continue
      def onComplete() = throw new IllegalStateException()
    }, BackPressured(5))

    buffer.observer.onNext(1)
    buffer.observer.onError(new RuntimeException("dummy"))
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onError when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = promise.future
      def onComplete() = throw new IllegalStateException()
    }, BackPressured(5))

    buffer.observer.onNext(1)
    buffer.observer.onNext(2)
    buffer.observer.onNext(3)
    buffer.observer.onNext(4)
    buffer.observer.onNext(5)
    buffer.observer.onError(DummyException("dummy"))

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = latch.countDown()
    }, BackPressured(5))

    buffer.observer.onComplete()
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
    }, BackPressured(5))

    buffer.observer.onNext(1)
    buffer.observer.onComplete()
    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](new Observer[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
    }, BackPressured(5))

    buffer.observer.onNext(1)
    buffer.observer.onNext(2)
    buffer.observer.onNext(3)
    buffer.observer.onNext(4)
    buffer.observer.onComplete()

    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should do onComplete only after all the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber[Long](new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
    }, BackPressured(10000))

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onComplete()
    startConsuming.success(Continue)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
    }, BackPressured(10000))

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onComplete()

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber[Long](new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
    }, BackPressured(10000))

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onError(new RuntimeException)
    startConsuming.success(Continue)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
    }, BackPressured(10000))

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onError(new RuntimeException)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }
}