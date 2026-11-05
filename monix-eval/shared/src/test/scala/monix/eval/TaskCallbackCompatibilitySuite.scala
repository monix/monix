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

import scala.annotation.nowarn
import scala.util.Success

object TaskCallbackCompatibilitySuite extends BaseTestSuite {
  @nowarn("cat=deprecation")
  private def legacyAsyncTask: Task[Int] =
    Task.async[Int](_.onSuccess(1))

  @nowarn("cat=deprecation")
  private def legacyCreateTask: Task[Int] =
    Task.create[Int] { (_, callback) =>
      callback.onSuccess(2)
      ()
    }

  test("deprecated callback builders remain source-compatible") { implicit scheduler =>
    val async = legacyAsyncTask.runToFuture
    val create = legacyCreateTask.runToFuture

    scheduler.tick()

    assertEquals(async.value, Some(Success(1)))
    assertEquals(create.value, Some(Success(2)))
  }
}
