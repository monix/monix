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

package monix.eval

import cats.Contravariant
import monix.execution.exceptions.DummyException

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

object CallbackSuite extends BaseTestSuite {
  case class TestCallback(
    success: Int => Unit = _ => (),
    error: Throwable => Unit = _ => ()) extends Callback[Int] {

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
    callback.onValue(1)
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
      { v => result = Some(Success(v)) },
      { e => result = Some(Failure(e)) })

    val stringCallback = callback.contramap[String](_.toInt)
    val dummy = new DummyException("dummy")

    stringCallback.onError(dummy)
    assertEquals(result, Some(Failure(dummy)))
  }

  test("contramap should invoke function before invoking callback") { implicit s =>
    val callback = TestCallback()
    val stringCallback = callback.contramap[String](_.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }

  test("contramap should invoke onError if the function throws") { implicit s =>
    val callback = TestCallback()
    val stringCallback = callback.contramap[String](_.toInt)
    stringCallback.onSuccess("not a int")
    assert(callback.errorCalled)
  }

  test("contramap has a cats Contramap instance") { implicit s =>
    val instance = implicitly[Contravariant[Callback]]
    val callback = TestCallback()
    val stringCallback = instance.contramap(callback)((x: String) => x.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }

  test("callback.apply(coeval) (success)") { _ =>
    var result = Option.empty[Try[Int]]
    val callback = TestCallback(
      { v => result = Some(Success(v)) },
      { e => result = Some(Failure(e)) })

    callback(Coeval(10))
    assert(callback.successCalled, "callback.successCalled")
    assert(!callback.errorCalled, "!callback.errorCalled")
    assertEquals(result, Some(Success(10)))
  }

  test("callback.apply(coeval) (error)") { _ =>
    var result = Option.empty[Try[Int]]
    val callback = TestCallback(
      { v => result = Some(Success(v)) },
      { e => result = Some(Failure(e)) })

    val dummy = new DummyException("dummy")
    callback(Coeval.raiseError(dummy))
    assert(!callback.successCalled, "!callback.successCalled")
    assert(callback.errorCalled, "callback.errorCalled")
    assertEquals(result, Some(Failure(dummy)))
  }

  test("Callback.fromPromise (success)") { _ =>
    val p = Promise[Int]()
    val cb = Callback.fromPromise(p)

    cb.onSuccess(1)
    assertEquals(p.future.value, Some(Success(1)))
    intercept[IllegalStateException] { cb.onSuccess(2) }
  }

  test("Callback.fromPromise (failure)") { _ =>
    val p = Promise[Int]()
    val cb = Callback.fromPromise(p)

    val dummy = new DummyException("dummy")
    cb.onError(dummy)

    assertEquals(p.future.value, Some(Failure(dummy)))
    intercept[IllegalStateException] { cb.onSuccess(1) }
  }

  test("Callback.empty reports errors") { implicit s =>
    val empty = Callback.empty[Int]
    val dummy = new DummyException("dummy")
    empty.onError(dummy)

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("SafeCallback protects against errors in onSuccess") { implicit s =>
    val dummy = new DummyException("dummy")
    var effect = 0

    val cb = new Callback[Int] {
      def onSuccess(value: Int): Unit = {
        effect += 1
        throw dummy
      }
      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
    }

    val safe = Callback.safe(cb)
    safe.onSuccess(1)

    assertEquals(effect, 1)
    assertEquals(s.state.lastReportedError, dummy)

    safe.onSuccess(1)
    assertEquals(effect, 1)
  }

  test("SafeCallback protects against errors in onError") { implicit s =>
    val dummy1 = new DummyException("dummy1")
    val dummy2 = new DummyException("dummy2")
    var effect = 0

    val cb = new Callback[Int] {
      def onSuccess(value: Int): Unit =
        throw new IllegalStateException("onSuccess")

      def onError(ex: Throwable): Unit = {
        effect += 1
        throw dummy1
      }
    }

    val safe = Callback.safe(cb)
    safe.onError(dummy2)

    assertEquals(effect, 1)
    assertEquals(s.state.lastReportedError, dummy1)

    safe.onError(dummy2)
    assertEquals(effect, 1)
  }
}
