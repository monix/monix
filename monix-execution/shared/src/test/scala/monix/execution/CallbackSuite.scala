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

package monix.execution

import minitest.TestSuite
import monix.execution.exceptions.{ CallbackCalledMultipleTimesException, DummyException }
import monix.execution.schedulers.TestScheduler

import scala.concurrent.Promise
import scala.util.{ Failure, Success, Try }

object CallbackSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  case class TestCallback(success: Int => Unit = _ => (), error: Throwable => Unit = _ => ())
    extends Callback[Throwable, Int] {

    var successCalled = false
    var errorCalled = false

    override def onSuccess(value: Int): Unit = {
      successCalled = true
      success(value)
    }

    override def onError(ex: Throwable): Unit = {
      errorCalled = true
      error(ex)
    }
  }

  test("onValue should invoke onSuccess") { implicit s =>
    val callback = TestCallback()
    callback.onSuccess(1)
    assert(callback.successCalled)
  }

  test("apply Success(value) should invoke onSuccess") { implicit s =>
    val callback = TestCallback()
    callback(Success(1))
    assert(callback.successCalled)
  }

  test("apply Failure(ex) should invoke onError") { implicit s =>
    val callback = TestCallback()
    callback(Failure(new IllegalStateException()))
    assert(callback.errorCalled)
  }

  test("contramap should pipe onError") { implicit s =>
    var result = Option.empty[Try[Int]]
    val callback = TestCallback(
      { v =>
        result = Some(Success(v))
      },
      { e =>
        result = Some(Failure(e))
      }
    )

    val stringCallback = callback.contramap[String](_.toInt)
    val dummy = DummyException("dummy")

    stringCallback.onError(dummy)
    assertEquals(result, Some(Failure(dummy)))
  }

  test("contramap should invoke function before invoking callback") { implicit s =>
    val callback = TestCallback()
    val stringCallback = callback.contramap[String](_.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }

  test("Callback.fromPromise (success)") { _ =>
    val p = Promise[Int]()
    val cb = Callback.fromPromise(p)

    cb.onSuccess(1)
    assertEquals(p.future.value, Some(Success(1)))
    intercept[IllegalStateException] { cb.onSuccess(2) }
    intercept[CallbackCalledMultipleTimesException] { cb.onSuccess(2) }
    ()
  }

  test("Callback.fromPromise (failure)") { _ =>
    val p = Promise[Int]()
    val cb = Callback.fromPromise(p)

    val dummy = DummyException("dummy")
    cb.onError(dummy)

    assertEquals(p.future.value, Some(Failure(dummy)))
    intercept[IllegalStateException] { cb.onSuccess(1) }
    intercept[CallbackCalledMultipleTimesException] { cb.onSuccess(1) }
    ()
  }

  test("Callback.empty reports errors") { implicit s =>
    val empty = Callback[Throwable].empty[Int]
    val dummy = DummyException("dummy")
    empty.onError(dummy)

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Callback.safe protects against errors in onSuccess") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val cb = new Callback[Throwable, Int] {
      def onSuccess(value: Int): Unit = {
        effect += 1
        throw dummy
      }
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    }

    val safe = Callback[Throwable].safe(cb)
    assert(safe.tryOnSuccess(1), "safe.tryOnSuccess(1)")

    assertEquals(effect, 1)
    assertEquals(s.state.lastReportedError, dummy)

    assert(!safe.tryOnSuccess(1))
    assertEquals(effect, 1)
  }

  test("Callback.safe protects against errors in onError") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")
    var effect = 0

    val cb = new Callback[Throwable, Int] {
      def onSuccess(value: Int): Unit =
        throw new IllegalStateException("onSuccess")

      def onError(ex: Throwable): Unit = {
        effect += 1
        throw dummy1
      }
    }

    val safe = Callback[Throwable].safe(cb)
    assert(safe.tryOnError(dummy2), "safe.onError(dummy2)")

    assertEquals(effect, 1)
    assertEquals(s.state.lastReportedError, dummy1)

    assert(!safe.tryOnError(dummy2), "!safe.onError(dummy2)")
    assertEquals(effect, 1)
  }

  test("callback(Right(a))") { _ =>
    val p = Promise[Int]()
    val cb = Callback.fromPromise(p)

    cb(Right(10))
    assertEquals(p.future.value, Some(Success(10)))
  }

  test("callback(Left(e))") { _ =>
    val p = Promise[Int]()
    val cb = Callback.fromPromise(p)
    val err = DummyException("dummy")

    cb(Left(err))
    assertEquals(p.future.value, Some(Failure(err)))
  }

  test("fromAttempt success") { _ =>
    val p = Promise[Int]()
    val cb = Callback[Throwable].fromAttempt[Int] {
      case Right(a) => p.success(a); ()
      case Left(e) => p.failure(e); ()
    }

    cb.onSuccess(10)
    assertEquals(p.future.value, Some(Success(10)))
  }

  test("fromAttempt error") { _ =>
    val p = Promise[Int]()
    val cb = Callback[Throwable].fromAttempt[Int] {
      case Right(a) => p.success(a); ()
      case Left(e) => p.failure(e); ()
    }

    val err = DummyException("dummy")
    cb.onError(err)
    assertEquals(p.future.value, Some(Failure(err)))
  }
}
