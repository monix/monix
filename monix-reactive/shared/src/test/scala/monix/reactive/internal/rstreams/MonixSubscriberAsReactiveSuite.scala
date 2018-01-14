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

package monix.reactive.internal.rstreams

import minitest.TestSuite
import monix.execution.Ack.{Continue, Stop}
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future

object MonixSubscriberAsReactiveSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    s.state.lastReportedError match {
      case null =>
        assert(s.state.tasks.isEmpty,
          "TestScheduler should have no pending tasks")
      case error =>
        throw error
    }
  }

  test("should work with synchronous batched requests") { implicit scheduler =>
    var sum = 0L
    var completed = false

    val observer = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit = {
        scheduler.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    Observable.range(0, 10000).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 128))

    scheduler.tick()
    assertEquals(sum, 5000L * 9999)
  }

  test("should work with Observer.Sync") { implicit scheduler =>
    var sum = 0L

    val observer = new Observer.Sync[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit = {
        scheduler.reportFailure(ex)
      }

      def onComplete(): Unit = ()
    }

    Observable.range(0, 10000).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 128))

    scheduler.tick()
    assertEquals(sum, 5000L * 9999)
  }

  test("should throw NullPointerException for null elements with Async Subscriber") { implicit s =>

    val observer = new Observer[Any] {
      def onNext(elem: Any) = Continue

      def onError(ex: Throwable): Unit = throw ex

      def onComplete(): Unit = ()
    }

    val reactiveSubscriber = Observer.toReactiveSubscriber[Any](observer)

    Observable(1, 2, 3).toReactivePublisher
      .subscribe(reactiveSubscriber)

    s.tick()

    intercept[NullPointerException]{
      reactiveSubscriber.onNext(null)
    }

    intercept[NullPointerException]{
      reactiveSubscriber.onError(null)
    }
  }

  test("should throw NullPointerException for null elements with Sync Subscriber") { implicit s =>

    val observer = new Observer.Sync[Any] {
      def onNext(elem: Any) = Continue

      def onError(ex: Throwable): Unit = throw ex

      def onComplete(): Unit = ()
    }

    val reactiveSubscriber = Observer.toReactiveSubscriber[Any](observer)

    Observable(1, 2, 3).toReactivePublisher
      .subscribe(reactiveSubscriber)

    s.tick()

    intercept[NullPointerException]{
      reactiveSubscriber.onNext(null)
    }

    intercept[NullPointerException]{
      reactiveSubscriber.onError(null)
    }
  }

  test("should work synchronously and with requests of size 1") { implicit s =>
    var completed = false
    var sum = 0L

    val observer = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit = {
        throw ex
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    val requested = 100
    Observable.range(0, requested).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 1))

    s.tick()
    assertEquals(sum, requested * (requested-1) / 2)
  }

  test("should work with asynchronous boundaries and batched requests") { implicit s =>
    var completed = false
    var sum = 0L

    val observer = new Observer[Long] {
      def onNext(elem: Long) =
        Future {
          sum += elem
          Continue
        }

      def onError(ex: Throwable): Unit = {
        throw ex
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    Observable.range(0, 10000).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 128))

    s.tick()
    assertEquals(sum, 5000L * 9999)
  }

  test("should work with asynchronous boundaries and requests of size 1") { implicit scheduler =>
    var completed = false
    var sum = 0L

    val observer = new Observer[Long] {
      def onNext(elem: Long) =
        Future {
          sum += elem
          Continue
        }

      def onError(ex: Throwable): Unit = {
        throw ex
      }

      def onComplete(): Unit = {
        completed = true
      }
    }

    Observable.range(0, 10000).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 1))

    scheduler.tick()
    assertEquals(sum, 5000L * 9999)
  }

  test("should cancel precisely with batched requests") { implicit s =>
    var completed = 1
    var sum = 0L

    val observer = new Observer[Long] {
      private[this] var received = 0

      def onNext(elem: Long) = Future {
        received += 1
        sum += elem

        if (received < 10)
          Continue
        else if (received == 10) {
          completed -= 1
          Stop
        }
        else
          throw new IllegalStateException(s"onNext($elem)")
      }

      def onError(ex: Throwable): Unit = {
        completed -= 1
        throw ex
      }

      def onComplete(): Unit = {
        completed -= 1
        throw new IllegalStateException("onComplete")
      }
    }

    Observable.range(1, 10000).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 128))

    s.tick()
    assertEquals(sum, 5 * 11)
    assertEquals(completed, 0)
  }

  test("should cancel precisely with requests of size 1") { implicit s =>
    for (i <- 0 until 100) {
      var completed = 0
      var sum = 0L

      val observer = new Observer[Long] {
        private[this] var received = 0

        def onNext(elem: Long) = Future {
          received += 1
          sum += elem

          if (received < 10)
            Continue
          else if (received == 10) {
            completed += 1
            Stop
          }
          else
            throw new IllegalStateException(s"onNext($elem)")
        }

        def onError(ex: Throwable): Unit = {
          completed += 1
          throw ex
        }

        def onComplete(): Unit = {
          completed += 1
          throw new IllegalStateException("onComplete")
        }
      }

      Observable.range(1, 10000).toReactivePublisher
        .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 1))

      s.tick()
      assertEquals(sum, 5 * 11)
      assertEquals(completed, 1)
    }
  }

  test("should cancel precisely for one element and batched requests") { implicit s =>
    var completed = 0
    var sum = 0L

    val observer = new Observer[Long] {
      private[this] var received = 0

      def onNext(elem: Long) = Future {
        received += 1
        sum += elem
        completed += 1
        Stop
      }

      def onError(ex: Throwable): Unit = {
        completed += 1
        throw ex
      }

      def onComplete(): Unit =
        completed += 1
    }

    Observable.now(100L).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 128))

    s.tick()
    assertEquals(sum, 100L)
    assert(completed >= 1, "completed >= 1")
  }

  test("should cancel precisely for one element and requests of size one") { implicit s =>
    var completed = 0
    var sum = 0L

    val observer = new Observer[Long] {
      private[this] var received = 0

      def onNext(elem: Long) = Future {
        received += 1
        sum += elem
        completed += 1
        Stop
      }

      def onError(ex: Throwable): Unit = {
        completed += 1
        throw ex
      }

      def onComplete(): Unit =
        completed += 1
    }

    Observable.now(100L).toReactivePublisher
      .subscribe(Observer.toReactiveSubscriber(observer, requestCount = 1))

    s.tick()
    assertEquals(sum, 100L)
    assert(completed >= 1, "completed >= 1")
  }
}