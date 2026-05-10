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

package monix.reactive.observers

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.Ack.Stop
import monix.execution.exceptions.BufferOverflowException
import monix.execution.exceptions.DummyException
import monix.reactive.OverflowStrategy.Fail
import monix.reactive.BaseConcurrencySuite
import monix.reactive.Observer

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Random

class OverflowStrategyFailConcurrencySuite extends BaseConcurrencySuite {
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

    await(completed, "completed")
    assertEquals(number, 100000)
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
      if (n > 0)
        s.execute(() => { buffer.onNext(n); loop(n - 1) })
      else
        buffer.onComplete()

    loop(10000)
    await(completed, "completed")
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

        def onComplete(): Unit = {
          ack.syncOnContinue(completed.countDown())
          ()
        }
      }

      val buffer = BufferedSubscriber[Long](Subscriber(underlying, s), Fail(total.toInt))
      for (i <- 1 to total.toInt) { buffer.onNext(i.toLong); () }
      buffer.onComplete()

      await(completed, "completed")
      assertEquals(received.toLong, total)
      assertEquals(sum, total * (total + 1) / 2)
    }
  }

  test("should trigger overflow when over capacity") { implicit s =>
    val errorCaught = new CountDownLatch(1)
    val receivedLatch = new CountDownLatch(5)
    val promise = Promise[Ack]()
    @volatile var errorThrown: Throwable = null

    val underlying = new Observer[Int] {
      var received = 0
      def onNext(elem: Int) = {
        received += 1
        if (received < 6) {
          receivedLatch.countDown()
          Continue
        } else if (received == 6) {
          receivedLatch.countDown()
          // never ending piece of processing
          promise.future
        } else
          Continue
      }

      def onError(ex: Throwable) = {
        errorThrown = ex
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

    await(receivedLatch, "receivedLatch")
    assert(!errorCaught.await(2, TimeUnit.SECONDS), "errorCaught.await should have failed")

    buffer.onNext(6)
    for (_ <- 0 until 100) buffer.onNext(7)

    promise.success(Continue)
    await(errorCaught, "errorCaught")
    assert(errorThrown.isInstanceOf[BufferOverflowException], s"Exception $errorThrown is not a buffer overflow error")
  }

  test("should send onError when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assertEquals(ex.getMessage, "dummy")
          latch.countDown()
        }

        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      Fail(5)
    )

    buffer.onError(new RuntimeException("dummy"))
    await(latch)

    val r = buffer.onNext(1)
    assertEquals(r, Stop)
  }

  test("should send onError when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assertEquals(ex.getMessage, "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = Continue
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      Fail(5)
    )

    buffer.onNext(1)
    buffer.onError(new RuntimeException("dummy"))
    await(latch)
  }

  test("should send onError when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assertEquals(ex.getMessage, "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = promise.future
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      Fail(5)
    )

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onNext(5)
    buffer.onError(DummyException("dummy"))

    promise.success(Continue)
    await(latch)
  }

  test("should send onComplete when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = latch.countDown()
        val scheduler = s
      },
      Fail(5)
    )

    buffer.onComplete()
    await(latch)
  }

  test("should send onComplete without back-pressure") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
        val scheduler = s
      },
      Fail(5)
    )

    buffer.onNext(1)
    buffer.onComplete()
    await(latch)
  }

  test("should send onComplete when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
        val scheduler = s
      },
      Fail(5)
    )

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onComplete()

    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    await(latch)
  }

  test("should do onComplete only after all the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue.type]()

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
        val scheduler = s
      },
      Fail(10000)
    )

    (0 until 9999).foreach { x => buffer.onNext(x.toLong) }
    buffer.onComplete()
    startConsuming.success(Continue)

    await(complete, "complete")
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
        val scheduler = s
      },
      Fail(10000)
    )

    (0 until 9999).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onComplete()

    await(complete, "complete")
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue.type]()

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      Fail(10000)
    )

    (0 until 9999).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onError(new RuntimeException)
    startConsuming.success(Continue)

    await(complete, "complete")
    assertEquals(sum, (0 until 9999).sum.toLong)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      Fail(10000)
    )

    (0 until 9999).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onError(new RuntimeException)

    await(complete, "complete")
    assertEquals(sum, (0 until 9999).sum.toLong)
  }
}
