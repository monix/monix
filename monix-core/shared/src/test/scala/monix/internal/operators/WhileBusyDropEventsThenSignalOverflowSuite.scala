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

package monix.internal.operators

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.broadcast.PublishProcessor
import monix.{Observer, Observable, Ack}
import monix.Ack.Continue
import monix.Observer

import scala.concurrent.Promise
import scala.util.Success


object WhileBusyDropEventsThenSignalOverflowSuite
  extends TestSuite[TestScheduler] {

  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should not drop events for synchronous observers") { implicit s =>
    val f = Observable.range(0, 1000)
      .whileBusyDropEvents(x => x).sum.asFuture

    s.tick()
    assertEquals(f.value, Some(Success(Some(999 * 500))))
  }

  test("should drop events for busy observers") { implicit s =>
    val source = PublishProcessor[Long]()
    val p = Promise[Continue]()
    var received = 0L
    var wasCompleted = false

    source.whileBusyDropEvents(x => x)
      .unsafeSubscribeFn(new Observer[Long] {
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

    source.onNext(1)
    assertEquals(received, 1 + 1 + 100)
    source.onNext(1)
    assertEquals(received, 102 + 1)
  }

  test("should send number of dropped events when onComplete") { implicit s =>
    val source = PublishProcessor[Long]()
    val p = Promise[Continue]()
    var received = 0L
    var wasCompleted = false

    source.whileBusyDropEvents(x => x)
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

    source.onNext(1)
    s.tick()
    assertEquals(received, 0)

    source.onComplete(); s.tick()
    assert(!wasCompleted, "wasCompleted should be false")

    p.success(Continue); s.tick()
    assert(wasCompleted, "wasCompleted should be true")
    assertEquals(received, 1)
  }
}
