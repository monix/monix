/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import cats.effect.IO
import minitest.TestSuite
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.{ Observable, Observer }
import monix.execution.FutureUtils.extensions._
import monix.execution.exceptions.DummyException

import scala.concurrent.duration._
import scala.concurrent.Future

object RepeatEvalFSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("should do sync evaluation in batches") { implicit s =>
    var wasCompleted = false
    var received = 0

    var i = 0
    val obs = Observable.repeatEvalF(IO { i += 1; i })

    val c = obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        received = elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assertEquals(received, Platform.recommendedBatchSize)
    s.tickOne()
    assertEquals(received, 2 * Platform.recommendedBatchSize)
    assert(!wasCompleted)
    c.cancel()
    s.tickOne()
    ()
  }

  test("should do back-pressure") { implicit s =>
    var wasCompleted = false
    var received = 0

    var i = 0
    val obs = Observable.repeatEvalF(IO.async[Int] { cb =>
      i += 1
      cb(Right(i))
    })

    val c = obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = Future.delayedResult(100.millis) {
        received = elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    s.tick(50.millis)
    assertEquals(received, 0)
    s.tick(50.millis)
    assertEquals(received, 1)
    s.tick(50.millis)
    assertEquals(received, 1)
    s.tick(50.millis)
    assertEquals(received, 2)
    c.cancel()
    s.tick(100.millis)
    assert(!wasCompleted)
  }

  test("should lift errors raised in F") { implicit s =>
    val dummy = DummyException("dummy")
    var errorThrown: Throwable = null

    val obs = Observable.repeatEvalF(IO.raiseError(dummy))

    obs.unsafeSubscribeFn(new Observer[Int] {
      override def onNext(elem: Int): Future[Ack] =
        throw new IllegalStateException("onNext should not happen")

      override def onError(ex: Throwable): Unit = {
        errorThrown = ex
      }

      override def onComplete(): Unit =
        throw new IllegalStateException("onComplete should not happen")
    })

    s.tickOne()
    assertEquals(errorThrown, dummy)
  }
}
