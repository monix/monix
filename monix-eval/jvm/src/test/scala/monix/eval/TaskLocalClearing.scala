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

import scala.concurrent.duration.Duration
import monix.execution.Scheduler.Implicits.global

import minitest.SimpleTestSuite


object TaskLocalClearing extends SimpleTestSuite {
  implicit val opts: Task.Options = Task.defaultOptions.enableLocalContextPropagation

  test("Task run loop is not side-effecting on context") {
    val local = TaskLocal(false).runSyncUnsafeOpt()
    def attempt = local.read flatMap {
      case false => local.bind(true) { Task.shift }
      case true => Task(fail("TaskLocal was not cleared"))
    }

    for (_ <- 1 to 1000) {
      attempt.runSyncUnsafeOpt(Duration.Inf)
    }
  }
}
