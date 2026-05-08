/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import cats.Contravariant

class CallbackInstanceSuite extends MUnitFunSuite {
  case class TestCallback(success: Int => Unit = _ => (), error: Throwable => Unit = _ => ())
    extends Callback[Throwable, Int] {

    var successCalled = false

    override def onSuccess(value: Int): Unit = {
      successCalled = true
      success(value)
    }

    override def onError(ex: Throwable): Unit =
      error(ex)
  }

  test("contramap has a cats Contramap instance") {
    val instance = implicitly[Contravariant[Callback[Throwable, *]]]
    val callback = TestCallback()
    val stringCallback = instance.contramap(callback)((x: String) => x.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }
}
