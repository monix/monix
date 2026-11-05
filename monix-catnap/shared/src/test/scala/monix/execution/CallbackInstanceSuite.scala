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
import minitest.TestSuite
import monix.execution.schedulers.TestScheduler

object CallbackInstanceSuite extends TestSuite[TestScheduler] {
  private final class TestCompletionCallback extends CompletionCallback[Int] {
    var successCalled = false
    override def onSuccess(value: Int): Unit = successCalled = true
    override def onError(error: Throwable): Unit = ()
    override def onCancellation(): Unit = ()
  }

  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("contramap has a cats Contramap instance") { _ =>
    val instance = implicitly[Contravariant[CompletionCallback[*]]]
    val callback = new TestCompletionCallback
    val stringCallback = instance.contramap(callback)((x: String) => x.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }
}
