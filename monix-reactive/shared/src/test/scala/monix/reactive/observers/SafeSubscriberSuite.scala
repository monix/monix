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

package monix.reactive.observers

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.schedulers.TestScheduler
import monix.execution.Ack.{Stop, Continue}
import monix.execution.exceptions.DummyException
import scala.concurrent.{Future, Promise}
import scala.util.Success

object SafeSubscriberSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty)
  }

  test("should protect against synchronous errors, test 1") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int): Future[Ack] = {
        throw new DummyException
      }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    val r = observer.onNext(1)
    assertEquals(r, Stop)
    assert(errorThrown.isInstanceOf[DummyException])

    val r2 = observer.onNext(1)
    assertEquals(r2, Stop)
    assert(s.state.lastReportedError == null)
  }

  test("should protect against synchronous errors, test 2") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int): Future[Ack] = {
        Future.failed(new DummyException)
      }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    val r = observer.onNext(1)
    assertEquals(r, Stop)
    assert(errorThrown.isInstanceOf[DummyException], "should receive DummyException")

    val r2 = observer.onNext(1)
    assertEquals(r2, Stop)
    assert(s.state.lastReportedError == null)
  }

  test("should protect against asynchronous errors") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int): Future[Ack] = {
        Future { throw new DummyException }
      }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    val r = observer.onNext(1)
    s.tick()

    assertEquals(r.value, Some(Success(Stop)))
    assert(errorThrown.isInstanceOf[DummyException])

    val r2 = observer.onNext(1); s.tick()
    assertEquals(r2.value, Some(Success(Stop)))
    assert(s.state.lastReportedError == null)
  }

  test("should protect against errors in onComplete") { implicit s =>
    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int) = Continue
      def onComplete(): Unit = {
        throw new DummyException()
      }

      def onError(ex: Throwable): Unit =
        fail("onError")
    })

    observer.onComplete()
    assert(s.state.lastReportedError.isInstanceOf[DummyException],
      "lastReportedError.isInstanceOf[DummyException]")
  }

  test("should protect against errors in onError") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int) = Continue
      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
        throw new DummyException("internal")
      }
    })

    observer.onError(new DummyException("external"))
    assertEquals(errorThrown, DummyException("external"))
    assertEquals(s.state.lastReportedError, DummyException("internal"))

    observer.onError(new DummyException("external 2"))
    assertEquals(errorThrown, DummyException("external"))
  }

  test("on synchronous cancel should block further signals") { implicit s =>
    var received = 0
    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int) = {
        received += 1
        Stop
      }
      def onComplete(): Unit = {
        received += 1
      }
      def onError(ex: Throwable): Unit = {
        received += 1
      }
    })

    assertEquals(observer.onNext(1), Stop)
    assertEquals(received, 1)
    assertEquals(observer.onNext(1), Stop)
    assertEquals(received, 1)
    observer.onComplete()
    assertEquals(received, 1)
    observer.onError(DummyException("external"))
    assertEquals(received, 1)
  }

  test("on asynchronous cancel should block further signals") { implicit s =>
    val p = Promise[Stop.type]()
    var received = 0

    val observer = SafeSubscriber(new Subscriber[Int] {
      val scheduler = s

      def onNext(elem: Int) = {
        received += 1
        p.future
      }

      def onComplete(): Unit = {
        received += 1
      }

      def onError(ex: Throwable): Unit = {
        received += 1
      }
    })

    val r = observer.onNext(1)
    assertEquals(r.value, None)
    s.tick()

    p.success(Stop)
    s.tick()

    assertEquals(r.value, Some(Success(Stop)))
    assertEquals(received, 1)

    observer.onComplete()
    observer.onError(new DummyException)

    s.tick()
    assertEquals(received, 1)
  }
}
