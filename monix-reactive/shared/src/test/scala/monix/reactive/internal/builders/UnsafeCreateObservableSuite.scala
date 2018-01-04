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
import monix.execution.Ack.{Stop, Continue}
import monix.execution.schedulers.TestScheduler
import monix.execution.{Ack, Cancelable}
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.util.Success

object UnsafeCreateObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "Scheduler should be left with no pending tasks")
  }

  test("should work") { implicit s =>
    val o = Observable.unsafeCreate[Int] { out =>
      out.onNext(1)
      out.onComplete()
      Cancelable.empty
    }

    val sum = o.sumF.runAsyncGetFirst
    s.tick()
    assertEquals(sum.value.get, Success(Some(1)))
  }

  test("should protect against user error") { implicit s =>
    val ex = DummyException("dummy")
    val o = Observable.unsafeCreate[Int] { out =>
      throw ex
    }

    val sum = o.sumF.runAsyncGetFirst
    s.tick()

    assertEquals(sum.value, None)
    assertEquals(s.state.lastReportedError, ex)
  }

  test("should protect the grammar onComplete") { implicit s =>
    var effect = 0
    val o = Observable.unsafeCreate[Int] { out =>
      out.onNext(10)
      out.onComplete()
      out.onNext(20)
      out.onComplete()
      Cancelable.empty
    }

    o.unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = effect += 1
      def onNext(elem: Int): Future[Ack] = {
        effect += 10
        Continue
      }
    })

    s.tick()
    assertEquals(effect, 11)
  }

  test("should protect the grammar on cancel") { implicit s =>
    var effect = 0
    val o = Observable.unsafeCreate[Int] { out =>
      out.onNext(10)
      out.onComplete()
      out.onNext(20)
      out.onComplete()
      Cancelable.empty
    }

    o.unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = effect += 1
      def onNext(elem: Int): Future[Ack] = {
        effect += 10
        Stop
      }
    })

    s.tick()
    assertEquals(effect, 10)
  }

  test("should protect the grammar on error") { implicit s =>
    val ex = DummyException("dummy")
    var effect = 0

    val o = Observable.unsafeCreate[Int] { out =>
      out.onNext(10)
      out.onError(ex)
      out.onNext(20)
      out.onError(ex)
      Cancelable.empty
    }

    o.unsafeSubscribeFn(new Observer[Int] {
      def onComplete(): Unit = {
        effect += 1000
        fail("should not complete")
      }

      def onError(ex: Throwable): Unit = effect += 1
      def onNext(elem: Int): Future[Ack] = {
        effect += 10
        Continue
      }
    })

    s.tick()
    assertEquals(effect, 11)
  }

  test("should protect against onNext synchronous exceptions") { implicit s =>
    val ex = DummyException("dummy")
    var effect = 0

    val o = Observable.unsafeCreate[Int] { out =>
      out.onNext(10)
      out.onError(ex)
      out.onNext(20)
      out.onError(ex)
      Cancelable.empty
    }

    o.unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable): Unit = effect += 1
      def onComplete(): Unit = fail("should not complete")
      def onNext(elem: Int): Future[Ack] = throw ex
    })

    s.tick()
    assertEquals(effect, 1)
  }
}