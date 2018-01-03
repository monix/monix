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
import monix.execution.Ack.Continue
import monix.execution.rstreams.SingleAssignmentSubscription
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.reactivestreams.{Subscriber, Subscription}

import scala.util.Success

object ObservableIsPublisherSuite extends TestSuite[TestScheduler] {
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

  test("should work with stop-and-wait back-pressure, test 1") { implicit scheduler =>
    var wasCompleted = false
    var sum = 0L

    Observable.range(0, 10000).toReactivePublisher.subscribe(new Subscriber[Long] {
      private[this] var s = null : Subscription

      def onSubscribe(s: Subscription): Unit = {
        this.s = s
        s.request(1)
      }

      def onNext(elem: Long): Unit = {
        sum += elem
        s.request(1)
      }

      def onError(ex: Throwable): Unit = {
        scheduler.reportFailure(ex)
      }

      def onComplete(): Unit = {
        wasCompleted = true
      }
    })

    scheduler.tick()
    assertEquals(sum, 5000 * 9999L)
  }

  test("should work with stop-and-wait back-pressure, test 2") { implicit s =>
    val out = PublishSubject[Long]()

    val subscription = SingleAssignmentSubscription()
    var wasCompleted = false
    var received = 0L
    var streamed = 0L

    out.doOnNext { _ => streamed += 1 }.toReactivePublisher.subscribe(
      new Subscriber[Long] {
        def onSubscribe(s: Subscription): Unit =
          subscription := s

        def onNext(elem: Long): Unit =
          received += 1

        def onError(ex: Throwable): Unit =
          s.reportFailure(ex)

        def onComplete(): Unit =
          wasCompleted = true
      })

    s.tick()

    // Pushing first element, should be back-pressured
    val f1 = out.onNext(1); s.tick()
    assertEquals(f1.value, None)
    assertEquals(streamed, 1)
    assertEquals(received, 0)

    // Making request for exactly one event
    subscription.request(1); s.tick()
    assertEquals(f1.value, Some(Success(Continue)))
    assertEquals(received, 1)

    // Pushing second element, should be back-pressured
    val f2 = out.onNext(1); s.tick()
    assertEquals(f2.value, None)
    assertEquals(streamed, 2)
    assertEquals(received, 1)

    // Making request for exactly one event
    subscription.request(1); s.tick()
    assertEquals(f2.value, Some(Success(Continue)))
    assertEquals(received, 2)

    // Pushing third element, should be back-pressured
    val f3 = out.onNext(1); s.tick()
    assertEquals(f3.value, None)
    assertEquals(streamed, 3)
    assertEquals(received, 2)

    // Pushing onComplete before f3 is done
    out.onComplete(); s.tick()
    assertEquals(received, 2)
    assert(!wasCompleted, "!wasCompleted")

    // Requesting event should unblock both onNext and onComplete
    subscription.request(1); s.tick()
    assertEquals(f3.value, Some(Success(Continue)))
    assertEquals(received, 3)
    assert(wasCompleted, "wasCompleted")
  }

  test("should work in batches of 1000, test 1") { implicit scheduler =>
    val range = 10000L
    val chunkSize = 1000

    var wasCompleted = false
    var sum = 0L

    Observable.range(0, range).toReactivePublisher
      .subscribe(new Subscriber[Long] {
        private[this] var s = null : Subscription
        private[this] var requested = chunkSize

        def onSubscribe(s: Subscription): Unit = {
          this.s = s
          s.request(requested)
        }

        def onNext(elem: Long): Unit = {
          sum += elem

          requested -= 1
          if (requested == 0) {
            s.request(chunkSize)
            requested = chunkSize
          }
        }

        def onError(ex: Throwable): Unit = {
          scheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          wasCompleted = true
        }
      })

    scheduler.tick()
    assertEquals(sum, range * (range-1) / 2)
    assert(wasCompleted)
  }

  test("should work in batches of 1000, test 2") { implicit scheduler =>
    val range = 10000L
    val chunkSize = 1000

    var wasCompleted = false
    var sum = 0L

    Observable.range(0, range).toReactivePublisher
      .subscribe(new Subscriber[Long] {
        private[this] var s = null : Subscription
        private[this] var requested = chunkSize

        def onSubscribe(s: Subscription): Unit = {
          this.s = s
          s.request(requested)
        }

        def onNext(elem: Long): Unit = {
          requested -= 1
          if (requested == 0) {
            s.request(chunkSize)
            requested = chunkSize
          }

          sum += elem
        }

        def onError(ex: Throwable): Unit = {
          scheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          wasCompleted = true
        }
      })

    scheduler.tick()
    assertEquals(sum, range * (range-1) / 2)
    assert(wasCompleted)
  }
}
