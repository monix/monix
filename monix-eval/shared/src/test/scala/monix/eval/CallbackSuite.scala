/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import cats.functor.Contravariant

import scala.util.{Failure, Success}

object CallbackSuite extends BaseTestSuite {

  case class TestCallback(success: Int => Unit = _ => (),
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

  test("contramp has a cats Contramap instance") { implicit s =>
    val instance = implicitly[Contravariant[Callback]]
    val callback = TestCallback()
    val stringCallback = instance.contramap(callback)((x: String) => x.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }
}
