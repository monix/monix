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

package monix.reactive.internal.operators

import monix.execution.BaseTestSuite

import monix.execution.Ack.Continue
import monix.execution.{ Ack, Scheduler }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration._

class DelayOnCompleteSuite extends BaseTestSuite {

  fixture.test("delayOnComplete should work") { s =>
    val obs = Observable.now(1).delayOnComplete(1.second)
    var received = 0
    var wasCompleted = 0

    obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s
      def onError(ex: Throwable): Unit = wasCompleted += 1
      def onComplete(): Unit = wasCompleted += 1

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        Continue
      }
    })

    s.tick()
    assertEquals(received, 1)
    assertEquals(wasCompleted, 0)

    s.tick(1.second)
    assertEquals(wasCompleted, 1)
  }

  fixture.test("delayOnComplete should be cancelable #1") { s =>
    val obs = Observable
      .now(1)
      .delayOnNext(1.second)
      .delayOnComplete(1.second)

    var received = 0
    var wasCompleted = 0

    val cancelable = obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s
      def onError(ex: Throwable): Unit = wasCompleted += 1
      def onComplete(): Unit = wasCompleted += 1

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        Continue
      }
    })

    cancelable.cancel()

    s.tick()
    assertEquals(received, 0)
    assertEquals(wasCompleted, 0)
  }

  fixture.test("delayOnComplete should be cancelable #2") { s =>
    val obs = Observable
      .now(1)
      .delayOnComplete(1.second)

    var received = 0
    var wasCompleted = 0

    val cancelable = obs.unsafeSubscribeFn(new Subscriber[Int] {
      implicit val scheduler: Scheduler = s
      def onError(ex: Throwable): Unit = wasCompleted += 1
      def onComplete(): Unit = wasCompleted += 1

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        Continue
      }
    })

    cancelable.cancel()

    s.tick()
    assertEquals(received, 1)
    assertEquals(wasCompleted, 0)

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }
}
