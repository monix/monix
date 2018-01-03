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
import monix.execution.Ack
import monix.execution.Ack.{Stop, Continue}
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observer, Observable}
import scala.concurrent.{Promise, Future}

object NowObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "Scheduler should be left with no pending tasks")
  }

  test("should emit one value synchronously") { implicit s =>
    var received = 0
    var completed = false

    Observable.now(1).unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        received += 1
        Continue
      }

      def onComplete(): Unit = {
        completed = true
      }

      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(received, 1)
    assert(completed)
  }

  test("should not do back-pressure on onComplete") { implicit s =>
    val p = Promise[Continue.type]()
    var onCompleteCalled = false
    var received = 0

    Observable.now(1).unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable) = throw ex

      def onNext(elem: Int): Future[Ack] = {
        received += 1
        p.future
      }

      def onComplete() = {
        onCompleteCalled = true
        received += 1
      }
    })

    assert(onCompleteCalled)
    assertEquals(received, 2)

    p.success(Continue); s.tick()
    assertEquals(received, 2)
  }

  test("should still send onComplete even if canceled synchronously") { implicit s =>
    var onCompleteCalled = false
    Observable.now(1).unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable) = throw ex
      def onNext(elem: Int) = Stop
      def onComplete(): Unit =
        onCompleteCalled = true
    })

    assert(onCompleteCalled)
  }

  test("should still send onComplete if canceled asynchronously") { implicit s =>
    val p = Promise[Ack]()
    var onCompleteCalled = false

    Observable.now(1).unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable) = throw ex
      def onNext(elem: Int) = p.future

      def onComplete() =
        onCompleteCalled = true
    })

    p.success(Stop)
    s.tick()

    assert(onCompleteCalled)
  }
}
