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

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.ExecutionModel.BatchedExecution
import monix.execution.exceptions.DummyException
import monix.reactive.OverflowStrategy.BackPressure
import monix.reactive.{ BaseConcurrencySuite, Observable, Observer }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }
import scala.util.Random

object OverflowStrategyBackPressuredConcurrencySuite extends BaseConcurrencySuite {
  test("merge test should work") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(1024))

    val num = 100000
    val source = Observable.repeat(1L).take(num.toLong)
    val o1 = source.map(_ + 2)
    val o2 = source.map(_ + 3)
    val o3 = source.map(_ + 4)

    val f = Observable
      .fromIterable(Seq(o1, o2, o3))
      .mergeMap(x => x)(BackPressure(100))
      .sum
      .runAsyncGetFirst

    val result = Await.result(f, 30.seconds)
    assertEquals(result, Some(num * 3L + num * 4L + num * 5L))
  }

  test("should do back-pressure") { implicit s =>
    // Repeating due to possible problems
    for (_ <- 0 until 10) {
      val promise = Promise[Ack]()
      val completed = new CountDownLatch(1)

      val buffer = BufferedSubscriber[Int](
        new Subscriber[Int] {
          def onNext(elem: Int) = promise.future
          def onError(ex: Throwable) = throw new IllegalStateException()
          def onComplete() = completed.countDown()
          val scheduler = s
        },
        BackPressure(8)
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
      buffer.onNext(10) // uncertain

      val async = buffer.onNext(11)
      assert(async != Continue)

      promise.success(Continue)
      Await.result(async, 10.seconds)

      assertEquals(buffer.onNext(1), Continue)
      assertEquals(buffer.onNext(2), Continue)
      assertEquals(buffer.onNext(3), Continue)
      assertEquals(buffer.onNext(4), Continue)
      assertEquals(buffer.onNext(5), Continue)
      assert(!completed.await(100, TimeUnit.MILLISECONDS), "completed.await shouldn't have succeeded")

      buffer.onComplete()
      assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
    }
  }

  test("should not lose events, test 1") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))

    // Repeating due to problems
    for (_ <- 0 until 10) {
      val count = 10000
      var received = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          received += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          s.reportFailure(ex)
          completed.countDown()
        }

        def onComplete(): Unit =
          completed.countDown()
      }

      val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), BackPressure(count))
      for (i <- 0 until count) buffer.onNext(i)
      buffer.onComplete()

      assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
      assert(received == count)
    }
  }

  test("should not lose events, test 2") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))
    val totalCount = 10000

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

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), BackPressure(totalCount))

    def loop(n: Int): Unit =
      if (n > 0) s.execute(() => {
        buffer.onNext(n); loop(n - 1)
      })
      else
        buffer.onComplete()

    loop(totalCount)
    assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
    assertEquals(number, totalCount)
  }

  test("should not lose events with async subscriber from one publisher (with sufficient buffer)") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))

    // Repeating because of possible problems
    for (_ <- 0 until 10) {
      val completed = new CountDownLatch(1)
      val total = 2000L

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

      val buffer = BufferedSubscriber[Long](Subscriber(underlying, s), BackPressure(total.toInt))
      for (i <- 1 to total.toInt) buffer.onNext(i.toLong)
      buffer.onComplete()

      assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
      assertEquals(received.toLong, total)
      assertEquals(sum, total * (total + 1) / 2)
    }
  }

  test("should not lose events with async subscriber from one publisher (with small buffer)") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))

    // Repeating because of possible problems
    for (_ <- 0 until 10) {
      val completed = new CountDownLatch(1)
      val total = 10000L

      var received = 0
      var sum = 0L

      val underlying = new Observer[Long] {
        var previous = 0L
        var ack: Future[Ack] = Continue

        def process(elem: Long): Ack = {
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

      val buffer = BufferedSubscriber[Long](Subscriber(underlying, s), BackPressure(256))
      for (i <- 1 to total.toInt) buffer.onNext(i.toLong)
      buffer.onComplete()

      assert(completed.await(15, TimeUnit.MINUTES), "completed.await should have succeeded")
      assertEquals(received.toLong, total)
      assertEquals(sum, total * (total + 1) / 2)
    }
  }

  test("should send onError when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage == "dummy")
          latch.countDown()
        }

        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      BackPressure(5)
    )

    buffer.onError(new RuntimeException("dummy"))
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")

    val r = buffer.onNext(1)
    assertEquals(r, Stop)
  }

  test("should send onError when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage == "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = Continue
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      BackPressure(5)
    )

    buffer.onNext(1)
    buffer.onError(new RuntimeException("dummy"))
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should send onError when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage == "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = promise.future
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      },
      BackPressure(5)
    )

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
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = latch.countDown()
        val scheduler = s
      },
      BackPressure(5)
    )

    buffer.onComplete()
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should send onComplete when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
        val scheduler = s
      },
      BackPressure(5)
    )

    buffer.onNext(1)
    buffer.onComplete()
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
    promise.success(Continue); ()
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
      BackPressure(5)
    )

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onComplete()

    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await should have succeeded")
  }

  test("should do onComplete only after all the queue was drained") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))
    val totalCount = 10000

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
      BackPressure(totalCount)
    )

    (0 until (totalCount - 1)).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onComplete()
    startConsuming.success(Continue)

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assert(sum == (0 until (totalCount - 1)).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))

    val totalCount = 10000
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
      BackPressure(totalCount)
    )

    (0 until (totalCount - 1)).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onComplete()

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assert(sum == (0 until (totalCount - 1)).sum)
  }

  test("should do onError only after the queue was drained") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))

    val totalCount = 10000
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
      BackPressure(totalCount)
    )

    (0 until (totalCount - 1)).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onError(new RuntimeException)
    startConsuming.success(Continue)

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assertEquals(sum, (0 until (totalCount - 1)).sum.toLong)
  }

  test("should do onError only after all the queue was drained, test2") { scheduler =>
    implicit val s = scheduler.withExecutionModel(BatchedExecution(128))
    val totalCount = 10000

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
      BackPressure(totalCount)
    )

    (0 until (totalCount - 1)).foreach { x => buffer.onNext(x.toLong); () }
    buffer.onError(new RuntimeException)

    assert(complete.await(15, TimeUnit.MINUTES), "complete.await should have succeeded")
    assertEquals(sum, (0 until (totalCount - 1)).sum.toLong)
  }
}
