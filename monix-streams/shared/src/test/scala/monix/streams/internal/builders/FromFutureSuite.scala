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

package monix.streams.internal.builders

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.ops._
import monix.execution.schedulers.TestScheduler
import monix.streams.exceptions.DummyException
import monix.streams.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object FromFutureSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("fromFuture should work for synchronous futures and synchronous observers") { implicit s =>
    val f = Future.successful(10)
    var received = 0
    var wasCompleted = false

    Observable.fromFuture(f).unsafeSubscribeFn(
      new Observer[Int] {
        def onNext(elem: Int) = {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = throw ex
        def onComplete(): Unit = {
          assert(!wasCompleted, "wasCompleted should be false")
          wasCompleted = true
        }
      })

    assertEquals(received, 10)
    assert(wasCompleted)
  }

  test("fromFuture should work for asynchronous futures and asynchronous observers") { implicit s =>
    val f = Future.delayedResult(100.millis)(10)
    var received = 0
    var wasCompleted = false

    Observable.fromFuture(f).unsafeSubscribeFn(
      new Observer[Int] {
        def onNext(elem: Int) = {
          received += elem
          Future.delayedResult(100.millis)(Continue)
        }

        def onError(ex: Throwable): Unit = throw ex
        def onComplete(): Unit = {
          assert(!wasCompleted, "wasCompleted should be false")
          wasCompleted = true
        }
      })

    s.tick(100.millis)
    assertEquals(received, 10)
    assert(!wasCompleted)
    s.tick(100.millis)
    assert(wasCompleted)
  }

  test("should emit onError for synchronous futures") { implicit s =>
    val f = Future.failed(DummyException("dummy"))
    var errorThrown: Throwable = null

    Observable.fromFuture(f).unsafeSubscribeFn(
      new Observer[Int] {
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = ()
      })

    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should emit onError for asynchronous futures") { implicit s =>
    val f = Future.delayedResult(100.millis)(throw DummyException("dummy"))
    var errorThrown: Throwable = null

    Observable.fromFuture(f).unsafeSubscribeFn(
      new Observer[Int] {
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = ()
      })

    s.tick(100.millis)
    assertEquals(errorThrown, DummyException("dummy"))
  }
}
