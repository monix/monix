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

package monix.internal.streams

import minitest.TestSuite
import scalax.concurrent.schedulers.TestScheduler
import monix.Observable
import org.reactivestreams.{Subscription, Subscriber}

object ObservableIsPublisherSuite extends TestSuite[TestScheduler] {
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

  test("should work with stop-and-wait back-pressure") { implicit scheduler =>
    var wasCompleted = false
    var sum = 0L

    Observable.range(0, 10000).subscribe(new Subscriber[Long] {
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

  test("should work in batches of 1000, test 1") { implicit scheduler =>
    val range = 10000L
    val chunkSize = 1000

    var wasCompleted = false
    var sum = 0L

    Observable.range(0, range)
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

    Observable.range(0, range)
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
