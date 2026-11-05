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

package monix.eval

import minitest.SimpleTestSuite
import monix.execution.{ Callback, Scheduler }
import monix.execution.schedulers.TestScheduler
import scala.annotation.nowarn
import scala.concurrent.Promise
import scala.util.Success

object TaskCallbackCompatibilitySuite extends SimpleTestSuite {
  @nowarn("cat=deprecation")
  private def legacyRegistrations: List[Task[Int]] =
    List(
      Task.async[Int](_.onSuccess(1)),
      Task.async0[Int]((_, callback) => callback.onSuccess(2)),
      Task.asyncF[Int](callback => Task.eval(callback.onSuccess(3))),
      Task.cancelable[Int](callback => { callback.onSuccess(4); Task.unit }),
      Task.cancelable0[Int]((_, callback) => { callback.onSuccess(5); Task.unit }),
      Task.cont[Int, Int]((callback, get) => { callback.onSuccess(6); get }),
      Task.cont0[Int, Int]((_, callback, get) => { callback.onSuccess(7); get })
    )

  @nowarn("cat=deprecation")
  private def legacyUnsafeRunAsync(promise: Promise[Int])(implicit scheduler: Scheduler): Unit = {
    val _ = Task.pure(8).unsafeRunAsync(Callback.fromPromise(promise))
  }

  test("deprecated registration APIs remain source-compatible") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val results = legacyRegistrations.map(_.unsafeRunToFuture())
    val promise = Promise[Int]()
    legacyUnsafeRunAsync(promise)

    scheduler.tick()

    assertEquals(results.map(_.value), (1 to 7).map(value => Some(Success(value))).toList)
    assertEquals(promise.future.value, Some(Success(8)))
  }
}
