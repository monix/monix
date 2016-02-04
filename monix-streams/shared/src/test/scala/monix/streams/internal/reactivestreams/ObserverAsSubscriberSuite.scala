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

package monix.streams.internal.reactivestreams

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.{Cancel, Continue}
import monix.streams.Observer

import scala.concurrent.Future

object ObserverAsSubscriberSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    s.state.get.lastReportedError match {
      case null =>
        assert(s.state.get.tasks.isEmpty,
          "TestScheduler should have no pending tasks")
      case error =>
        throw error
    }
  }

  test("should work synchronously with batched requests") { implicit scheduler =>
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

    Observable.range(0, 10000)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 128))

    scheduler.tick()
    assertEquals(sum, 5000L * 9999)
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

    Observable.range(0, 10000)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 1))

    s.tick()
    assertEquals(sum, 5000L * 9999)
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

    Observable.range(0, 10000)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 128))

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

    Observable.range(0, 10000)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 1))

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
          Cancel
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

    Observable.range(1, 10000)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 128))

    s.tick()
    assertEquals(sum, 5 * 11)
    assertEquals(completed, 0)
  }

  test("should cancel precisely with requests of size 1") { implicit s =>
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
          Cancel
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

    Observable.range(1, 10000)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 1))

    s.tick()
    assertEquals(sum, 5 * 11)
    assertEquals(completed, 0)
  }

  test("should cancel precisely for one element and batched requests") { implicit s =>
    var completed = 1
    var sum = 0L

    val observer = new Observer[Long] {
      private[this] var received = 0

      def onNext(elem: Long) = Future {
        received += 1
        sum += elem
        completed -= 1
        Cancel
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

    Observable.now(100L)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 128))

    s.tick()
    assertEquals(sum, 100L)
    assertEquals(completed, 0)
  }

  test("should cancel precisely for one element and requests of size one") { implicit s =>
    var completed = 1
    var sum = 0L

    val observer = new Observer[Long] {
      private[this] var received = 0

      def onNext(elem: Long) = Future {
        received += 1
        sum += elem
        completed -= 1
        Cancel
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

    Observable.now(100L)
      .subscribe(Observer.toReactiveSubscriber(observer, bufferSize = 1))

    s.tick()
    assertEquals(sum, 100L)
    assertEquals(completed, 0)
  }
}
