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

package monix.reactive.internal.builders

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object RangeObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should not have pending tasks left")
  }

  test("should do increments and synchronous observers") { implicit s =>
    var wasCompleted = false
    var sum = 0L

    Observable.range(1, 10, 1).unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete(): Unit = wasCompleted = true
      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(sum, 45)
    assertEquals(wasCompleted, true)
  }

  test("should do decrements and synchronous observers") { implicit s =>
    var wasCompleted = false
    var sum = 0L

    Observable.range(9, 0, -1).unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete(): Unit = wasCompleted = true
      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(sum, 45)
    assertEquals(wasCompleted, true)
  }

  test("should do back-pressure") { implicit s =>
    var wasCompleted = false
    var received = 0L
    var sum = 0L

    Observable.range(1, 5).unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        Future.delayedResult(1.second) {
          sum += elem
          Continue
        }
      }

      def onComplete(): Unit = wasCompleted = true
      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(sum, 0); assertEquals(received, 1)
    s.tick(1.second); assertEquals(sum, 1); assertEquals(received, 2)
    s.tick(1.second); assertEquals(sum, 3); assertEquals(received, 3)
    s.tick(1.second); assertEquals(sum, 6); assertEquals(received, 4)
    s.tick(1.second); assertEquals(sum, 10); assertEquals(received, 4)
    assert(wasCompleted)
  }

  test("should throw if step is zero") { implicit s =>
    intercept[IllegalArgumentException] {
      Observable.range(0, 10, 0)
    }
  }

  test("should do synchronous execution in batches") { implicit s =>
    val batchSize = s.executionModel.recommendedBatchSize
    var received = 0

    Observable.range(0, batchSize * 20).map(_ => 1)
      .subscribe { x => received += 1; Continue }

    for (idx <- 1 to 20) {
      assertEquals(received, Platform.recommendedBatchSize * idx)
      s.tickOne()
    }
  }

  test("should be cancelable") { implicit s =>
    var received = 0
    var wasCompleted = 0
    val source = Observable.range(0, Platform.recommendedBatchSize * 10)

    val cancelable = source.unsafeSubscribeFn(new Subscriber[Long] {
      implicit val scheduler = s

      def onNext(elem: Long) = {
        received += 1
        Continue
      }

      def onError(ex: Throwable) = wasCompleted += 1
      def onComplete() = wasCompleted += 1
    })

    cancelable.cancel()
    s.tick()

    assertEquals(received, s.executionModel.recommendedBatchSize * 2)
    assertEquals(wasCompleted, 0)
  }
}
