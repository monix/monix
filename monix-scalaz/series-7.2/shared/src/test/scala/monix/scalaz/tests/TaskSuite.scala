/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.scalaz.tests

import scalaz.Scalaz._
import minitest.SimpleTestSuite
import monix.scalaz._
import monix.eval.Task
import monix.eval.Task.nondeterminism
import monix.execution.schedulers.TestScheduler
import scala.concurrent.duration._
import scala.util.Success

object TaskSuite extends SimpleTestSuite {
  test("tasks should execute in parallel") {
    implicit val s = TestScheduler()

    val task1 = Task(1).delayExecution(1.second)
    val task2 = Task(2).delayExecution(1.second)
    val both = (task1 |@| task2)(_ + _)

    val f = both.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Success(3)))
  }
}
