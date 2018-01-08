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

import minitest.SimpleTestSuite
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object IntervalObservableSuite extends SimpleTestSuite {
  test("should do intervalWithFixedDelay") {
    implicit val s = TestScheduler()
    var received = 0

    Observable.intervalWithFixedDelay(1.second).unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        received += 1
        Future.delayedResult(100.millis)(Continue)
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = ()
    })

    assertEquals(received, 1)

    s.tick()
    assertEquals(received, 1)

    s.tick(1.second)
    assertEquals(received, 1)
    s.tick(100.millis)
    assertEquals(received, 2)

    s.tick(1.second + 100.millis)
    assertEquals(received, 3)
  }

  test("intervalWithFixedDelay should be cancelable") {
    implicit val s = TestScheduler()
    var received = 0
    var wasCompleted = false

    val cancelable = Observable.intervalWithFixedDelay(1.second)
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long): Future[Ack] = {
          received += 1
          Continue
        }

        def onError(ex: Throwable) = wasCompleted = false
        def onComplete() = wasCompleted = false
      })

    s.tick(1.second)
    assertEquals(received, 2)

    cancelable.cancel()
    assertEquals(received, 2)
    assert(!wasCompleted)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("should do intervalAtFixedRate") {
    implicit val s = TestScheduler()
    var received = 0

    Observable.intervalAtFixedRate(1.second).unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        received += 1
        Future.delayedResult(100.millis)(Continue)
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = ()
    })

    assertEquals(received, 1)

    s.tick(900.millis)
    assertEquals(received, 1)

    s.tick(100.millis)
    assertEquals(received, 2)

    s.tick(900.millis)
    assertEquals(received, 2)

    s.tick(100.millis)
    assertEquals(received, 3)
  }
}
