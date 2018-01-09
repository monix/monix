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
import monix.execution.Ack.Continue
import monix.execution.schedulers.TestScheduler
import monix.execution.exceptions.DummyException
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import scala.concurrent.Promise
import scala.util.Success

object WhileBusyDropEventsSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should not drop events for synchronous observers") { implicit s =>
    val f = Observable.range(0, 1000).whileBusyDropEvents.sumF.runAsyncGetFirst
    s.tick()

    assertEquals(f.value, Some(Success(Some(999 * 500))))
  }

  test("should drop events for busy observers") { implicit s =>
    val source = PublishSubject[Long]()
    val p = Promise[Continue.type]()
    var received = 0L
    var wasCompleted = false

    source.whileBusyDropEvents.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += elem
        p.future
      }

      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    source.onNext(1)
    s.tick()
    assertEquals(received, 1)

    for (i <- 0 until 100) source.onNext(i)
    assertEquals(received, 1)

    p.success(Continue)
    s.tick()
    assertEquals(received, 1)

    for (i <- 100 until 200) source.onNext(i)
    assertEquals(received, (100 until 200).sum + 1)
  }

  test("onComplete should not apply back-pressure") { implicit s =>
    val source = PublishSubject[Long]()
    val p = Promise[Continue.type]()
    var received = 0L
    var wasCompleted = false

    source.whileBusyDropEvents
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) =
          p.future.map { continue =>
            received += elem
            continue
          }

        def onError(ex: Throwable) = ()
        def onComplete() = {
          wasCompleted = true
        }
      })

    source.onNext(1); s.tick()
    assertEquals(received, 0)

    source.onComplete(); s.tick()
    assertEquals(received, 0)
    assert(wasCompleted, "wasCompleted should be true")

    p.success(Continue)
    s.tick()
    assertEquals(received, 1)
  }

  test("onError should not apply back-pressure") { implicit s =>
    val source = PublishSubject[Long]()
    val p = Promise[Continue.type]()
    var received = 0L
    var wasThrown = null : Throwable

    source.whileBusyDropEvents.unsafeSubscribeFn(
      new Observer[Long] {
        def onNext(elem: Long) =
          p.future.map { continue =>
            received += elem
            continue
          }

        def onError(ex: Throwable) = wasThrown = ex
        def onComplete() = ()
      })

    val ex = DummyException("dummy")

    source.onNext(1); s.tick()
    assertEquals(received, 0)

    source.onError(ex); s.tick()
    assertEquals(received, 0)
    assertEquals(wasThrown, ex)

    p.success(Continue); s.tick()
    assertEquals(received, 1)
  }
}
