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

import cats.laws._
import cats.laws.discipline._

import scala.util.Success

object TaskStartAndForgetSuite extends BaseTestSuite {

  test("task.startAndForget works") { implicit sc =>
    check1 { (i: Int) =>
      var counter = 0
      val task = Task {
        counter = i
        i
      }
      task.startAndForget.runAsync
      sc.tick()

      counter <-> i
    }
  }

  test("task.startAndForget is stack safe") { implicit sc =>
    var task: Task[Any] = Task(1)
    for (_ <- 0 until 5000) task = task.startAndForget
    for (_ <- 0 until 5000) task = task.flatMap(_ => Task.unit)

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("task.startAndForget <-> task.start.map(_ => ())") { implicit sc =>
    check1 { (task: Task[Int]) =>
      task.startAndForget <-> task.start.map(_ => ())
    }
  }
}