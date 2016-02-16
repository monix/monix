/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.observers

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.Ack.{Cancel, Continue}
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.streams.OverflowStrategy.Fail
import monix.streams.exceptions.{BufferOverflowException, DummyException}
import monix.streams.internal.concurrent.RunnableAction
import monix.streams.{Observer, OverflowStrategy, Subscriber}
import scala.concurrent.{Future, Promise}

object BufferOverflowTriggeringSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should not lose events, test 1") { implicit s =>
    var number = 0
    var wasCompleted = false

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        wasCompleted = true
      }
    }

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), Fail(1000))
    for (i <- 0 until 1000) buffer.onNext(i)
    buffer.onComplete()

    assert(!wasCompleted)
    s.tick()
    assert(number == 1000)
    assert(wasCompleted)
  }

  test("should not lose events, test 2") { implicit s =>
    var number = 0
    var completed = false

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), Fail(1000))

    def loop(n: Int): Unit =
      if (n > 0)
        s.execute(RunnableAction { buffer.onNext(n); loop(n-1) })
      else
        buffer.onComplete()

    loop(10000)
    assert(!completed)
    assertEquals(number, 0)

    s.tick()
    assert(completed)
    assertEquals(number, 10000)
  }

  test("should trigger overflow when over capacity") { implicit s =>
    var errorCaught: Throwable = null
    var receivedLatch = 5
    val promise = Promise[Ack]()

    val underlying = new Observer[Int] {
      var received = 0
      def onNext(elem: Int) = {
        received += 1
        if (received < 6) {
          receivedLatch -= 1
          Continue
        }
        else if (received == 6) {
          receivedLatch -= 1
          // never ending piece of processing
          promise.future
        }
        else
          Continue
      }

      def onError(ex: Throwable) = {
        assert(ex.isInstanceOf[BufferOverflowException],
          s"Exception $ex is not a buffer overflow error")
        errorCaught = ex
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

    for (_ <- 0 until 1000; if receivedLatch > 0) s.tickOne()
    assertEquals(receivedLatch, 0)
    assertEquals(errorCaught, null)

    buffer.onNext(6)
    for (i <- 0 until 10) buffer.onNext(7)

    promise.success(Continue)
    s.tickOne()
    assert(errorCaught != null && errorCaught.isInstanceOf[BufferOverflowException])
  }

  test("should send onError when empty") { implicit s =>
    var errorThrown: Throwable = null
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          errorThrown = ex
        }

        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      }, Fail(5))

    buffer.onError(DummyException("dummy"))
    s.tickOne()

    assertEquals(errorThrown, DummyException("dummy"))
    val r = buffer.onNext(1)
    assertEquals(r, Cancel)
  }

  test("should send onError when in flight") { implicit s =>
    var errorThrown: Throwable = null
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          errorThrown = ex
        }
        def onNext(elem: Int) = Continue
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      }, Fail(5))

    buffer.onNext(1)
    buffer.onError(DummyException("dummy"))
    s.tickOne()

    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should send onError when at capacity") { implicit s =>
    var errorThrown: Throwable = null
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          errorThrown = ex
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
    s.tick()

    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should send onComplete when empty") { implicit s =>
    var wasCompleted = false
      val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = wasCompleted = true
        val scheduler = s
      }, Fail(5))

    buffer.onComplete()
    s.tickOne()
    assert(wasCompleted)
  }

  test("should send onComplete when in flight") { implicit s =>
    var wasCompleted = false
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = wasCompleted = true
        val scheduler = s
      }, Fail(5))

    buffer.onNext(1)
    buffer.onComplete()
    s.tick()
    assert(!wasCompleted)

    promise.success(Continue)
    s.tick()
    assert(wasCompleted)
  }

  test("should send onComplete when at capacity") { implicit s =>
    var wasCompleted = false
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = wasCompleted = true
        val scheduler = s
      }, Fail(5))

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onComplete()

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

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = wasCompleted = true
        val scheduler = s
      }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onComplete()
    startConsuming.success(Continue)

    s.tick()
    assert(wasCompleted)
    assert(sum == (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    var wasCompleted = false

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = wasCompleted = true
        val scheduler = s
      }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onComplete()
    s.tick()

    assert(wasCompleted)
    assert(sum == (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    var errorThrown: Throwable = null
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }

        def onError(ex: Throwable) = errorThrown = ex
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onError(DummyException("dummy"))
    startConsuming.success(Continue)

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    var errorThrown: Throwable = null

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = errorThrown = ex
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      }, Fail(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onError(DummyException("dummy"))

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do synchronous execution in batches") { implicit s =>
    var received = 0L
    var wasCompleted = false

    val buffer = BufferedSubscriber[Long](
      new Subscriber[Long] {
        def onNext(elem: Long) = {
          received += 1
          Continue
        }
        def onError(ex: Throwable) = ()
        def onComplete() = wasCompleted = true
        val scheduler = s
      }, Fail(Platform.recommendedBatchSize * 3))

    for (i <- 0 until (Platform.recommendedBatchSize * 2)) buffer.onNext(i)
    buffer.onComplete()
    assertEquals(received, 0)

    s.tickOne()
    assertEquals(received, Platform.recommendedBatchSize)
    s.tickOne()
    assertEquals(received, Platform.recommendedBatchSize * 2)
    s.tickOne()
    assertEquals(wasCompleted, true)
  }
}
