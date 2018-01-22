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

package monix.reactive.observers

import java.util.concurrent.{CountDownLatch, TimeUnit}
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.exceptions.{BufferOverflowException, DummyException}
import monix.reactive.OverflowStrategy.Fail
import monix.reactive.{BaseConcurrencySuite, Observer}
import scala.concurrent.{Future, Promise}
import scala.util.Random


object OverflowStrategyFailConcurrencySuite extends BaseConcurrencySuite {
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

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), Fail(100000))
    for (i <- 0 until 100000) buffer.onNext(i)
    buffer.onComplete()

    assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
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

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), Fail(100000))

    def loop(n: Int): Unit =
      if (n > 0) s.execute(new Runnable {
        def run() = { buffer.onNext(n); loop(n-1) }
      })
      else buffer.onComplete()

    loop(10000)
    assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
    assertEquals(number, 10000)
  }

  test("should not lose events with async subscriber from one publisher") { implicit s =>
    // Repeating because of possible problems
    for (_ <- 0 until 100) {
      val completed = new CountDownLatch(1)
      val total = 10000L

      var received = 0
      var sum = 0L

      val underlying = new Observer[Long] {
        var previous = 0L
        var ack: Future[Ack] = Continue

        def process(elem: Long): Ack = {
          assertEquals(elem, previous + 1)
          received += 1
          sum += elem
          previous = elem
          Continue
        }

        def onNext(elem: Long): Future[Ack] = {
          val goAsync = Random.nextInt() % 2 == 0
          ack = if (goAsync) Future(process(elem)) else process(elem)
          ack
        }

        def onError(ex: Throwable): Unit =
          s.reportFailure(ex)

        def onComplete(): Unit =
          ack.syncOnContinue(completed.countDown())
      }

      val buffer = BufferedSubscriber[Long](Subscriber(underlying, s), Fail(total.toInt))
      for (i <- 1 to total.toInt) buffer.onNext(i)
      buffer.onComplete()

      assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
      assertEquals(received, total)
      assertEquals(sum, total * (total + 1) / 2)
    }
  }

  test("should trigger overflow when over capacity") { implicit s =>
    val errorCaught = new CountDownLatch(1)
    val receivedLatch = new CountDownLatch(5)
    val promise = Promise[Ack]()

    val underlying = new Observer[Int] {
      var received = 0
      def onNext(elem: Int) = {
        received += 1
        if (received < 6) {
          receivedLatch.countDown()
          Continue
        }
        else if (received == 6) {
          receivedLatch.countDown()
          // never ending piece of processing
          promise.future
        }
        else
          Continue
      }

      def onError(ex: Throwable) = {
        assert(ex.isInstanceOf[BufferOverflowException],
          s"Exception $ex is not a buffer overflow error")
        errorCaught.countDown()
      }

      def onComplete() = {
        throw new IllegalStateException("Should not onComplete")
      }
    }

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), Fail(5))

    assertEquals(buffer.onNext(1), Continue)
    assertEquals(buffer.onNext(2), Continue)
    assertEquals(buffer.onNext(3), Continue)
    assertEquals(buffer.onNext(4), Continue)
    assertEquals(buffer.onNext(5), Continue)

    assert(receivedLatch.await(15, TimeUnit.MINUTES), "receivedLatch.await should have succeeded")
    assert(!errorCaught.await(2, TimeUnit.SECONDS), "errorCaught.await should have failed")

    buffer.onNext(6)
    for (_ <- 0 until 100) buffer.onNext(7)

    promise.success(Continue)
    assert(errorCaught.await(15, TimeUnit.MINUTES), "errorCaught.await should have succeeded")
  }

  test("should send onError when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }

      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, Fail(5))

    buffer.onError(new RuntimeException("dummy"))
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")

    val r = buffer.onNext(1)
    assertEquals(r, Stop)
  }

  test("should send onError when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = Continue
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, Fail(5))

    buffer.onNext(1)
    buffer.onError(new RuntimeException("dummy"))
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should send onError when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = promise.future
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, Fail(5))

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onNext(5)
    buffer.onError(DummyException("dummy"))

    promise.success(Continue)
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should send onComplete when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = latch.countDown()
      val scheduler = s
    }, Fail(5))

    buffer.onComplete()
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should send onComplete without back-pressure") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
      val scheduler = s
    }, Fail(5))

    buffer.onNext(1)
    buffer.onComplete()
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should send onComplete when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
      val scheduler = s
    }, Fail(5))

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onComplete()

    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should do onComplete only after all the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue.type]()

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
      val scheduler = s
    }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onComplete()
    startConsuming.success(Continue)

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
      val scheduler = s
    }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onComplete()

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue.type]()

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onError(new RuntimeException)
    startConsuming.success(Continue)

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onError(new RuntimeException)

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }
}