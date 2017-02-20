/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.reactive.exceptions.DummyException
import monix.reactive.subjects.PublishSubject
import monix.reactive.{BaseLawsTestSuite, Observable, Observer}

import scala.concurrent.Future
import scala.concurrent.duration._

object MeasureDownstreamLatencySuite extends BaseLawsTestSuite {
  test("measurements work") { implicit s =>
    var received = 0
    var lastDuration = 0L

    val source = Observable
      .repeat(1L)
      .measureDownstreamLatency { ms => lastDuration = ms }

    val sub = source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) =
        Future.delayedResult(1.second) {
          received += 1
          Continue
        }

      def onError(ex: Throwable) = throw ex
      def onComplete() = ()
    })

    for (i <- 1 until 10) {
      s.tick(1.second)
      assertEquals(received, i)
      assertEquals(lastDuration, 1000)
    }

    sub.cancel()
    s.tick(1.hour)
    assertEquals(received, 11)
  }

  test("protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    var wasThrown: Throwable = null
    val source = Observable
      .repeat(1L)
      .executeWithFork
      .measureDownstreamLatency { _ => throw dummy }

    val sub = source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = Continue
      def onComplete() = ()
      def onError(ex: Throwable) =
        wasThrown = ex
    })

    s.tick()
    assertEquals(wasThrown, dummy)
  }

  test("triggers onComplete for async subscriber") { implicit s =>
    var received = 0
    var lastDuration = 0L
    var wasCompleted = false

    val source = Observable
      .repeat(1L)
      .take(100)
      .measureDownstreamLatency { ms => lastDuration = ms }

    val sub = source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) =
        Future.delayedResult(1.second) {
          received += 1
          Continue
        }

      def onError(ex: Throwable) = throw ex
      def onComplete() = wasCompleted = true
    })

    s.tick(1.second * 100)
    assertEquals(received, 100)
    assertEquals(lastDuration, 1000)
    assert(wasCompleted, "wasCompleted")
  }

  test("triggers onComplete for sync subscriber") { implicit s =>
    var received = 0
    var lastDuration = 0L
    var wasCompleted = false

    val source = Observable
      .repeat(1L)
      .take(100)
      .measureDownstreamLatency { ms => lastDuration = ms }

    val sub = source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        Continue
      }

      def onError(ex: Throwable) = throw ex
      def onComplete() = wasCompleted = true
    })

    s.tick()
    assertEquals(received, 100)
    assertEquals(lastDuration, 0)
    assert(wasCompleted, "wasCompleted")
  }

  test("reports error by Scheduler if onError already happened") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")
    var wasThrown: Throwable = null

    val subject = PublishSubject[Long]()
    val source = subject.measureDownstreamLatency { _ => throw dummy1 }

    val sub = source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) =
        Future.delayedResult(1.second)(Continue)

      def onComplete() = ()
      def onError(ex: Throwable) =
        wasThrown = ex
    })

    subject.onNext(1)
    subject.onError(dummy2)

    assertEquals(wasThrown, dummy2)
    assertEquals(s.state.lastReportedError, null)

    s.tick(1.second)
    assertEquals(s.state.lastReportedError, dummy1)
  }
}
