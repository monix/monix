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

package monix.reactive.internal.operators

import minitest.TestSuite
import monix.execution.Ack.{Stop, Continue}
import monix.execution.schedulers.TestScheduler
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer}
import scala.concurrent.{Future, CancellationException}
import scala.concurrent.duration._

object OnCancelTriggerErrorSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should work") { implicit s =>
    var errorThrow: Throwable = null
    var effect = 0
    val obs = Observable.now(1).delaySubscription(1.second).onCancelTriggerError

    val c = obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = { effect += elem; Continue }
      def onError(ex: Throwable): Unit = errorThrow = ex
      def onComplete(): Unit = fail("onComplete")
    })

    c.cancel()
    assertEquals(effect, 0)
    assert(errorThrow != null && errorThrow.isInstanceOf[CancellationException],
      "errorThrow should be CancellationException")
  }

  test("cannot cancel after complete") { implicit s =>
    var errorThrow: Throwable = null
    var effect = 0
    val obs = Observable.now(1).onCancelTriggerError

    val c = obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = { effect += elem; Continue }
      def onError(ex: Throwable): Unit = errorThrow = ex
      def onComplete(): Unit = effect += 20
    })

    c.cancel()
    assertEquals(effect, 21)
    assertEquals(errorThrow, null)
  }

  test("cannot cancel after error") { implicit s =>
    val dummy = DummyException("dummy")
    var errorThrow: Throwable = null
    var effect = 0

    val obs: Observable[Int] = Observable.raiseError(dummy).onCancelTriggerError

    val c = obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = { effect += elem; Continue }
      def onError(ex: Throwable): Unit = errorThrow = ex
      def onComplete(): Unit = fail("onComplete")
    })

    c.cancel()
    assertEquals(effect, 0)
    assertEquals(errorThrow, dummy)
  }

  test("cannot cancel after asynchronous stop") { implicit s =>
    var errorThrow: Throwable = null
    var effect = 0
    val obs = Observable.now(1).delayOnComplete(1.second).onCancelTriggerError

    val c = obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = Future { effect += elem; Stop }
      def onError(ex: Throwable): Unit = errorThrow = ex
      def onComplete(): Unit = effect += 20
    })

    s.tick(); c.cancel()
    assertEquals(effect, 1)
    assertEquals(errorThrow, null)
  }
}
