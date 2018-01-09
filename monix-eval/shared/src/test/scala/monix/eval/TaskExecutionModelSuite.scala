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

import monix.execution.ExecutionModel.AlwaysAsyncExecution
import scala.util.Success

object TaskExecutionModelSuite extends BaseTestSuite {
  test("Task.now.executeWithModel(AlwaysAsyncExecution) should work") { implicit s =>
    val task = Task.now(1).executeWithModel(AlwaysAsyncExecution)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now should not be async with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val task = Task.now(1)
    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.eval.executeWithModel(AlwaysAsyncExecution) should work") { implicit s =>
    val task = Task.eval(1).executeWithModel(AlwaysAsyncExecution)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.eval should be async with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val task = Task.eval(1)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.flatMap loops should work with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)

    def loop(count: Int): Task[Int] =
      Task.now(count).flatMap { nr =>
        if (nr > 0) loop(count-1)
        else Task.now(nr)
      }

    val task = loop(100)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  test("Task.eval.flatMap loops should work with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)

    def loop(count: Int): Task[Int] =
      Task.eval(count).flatMap { nr =>
        if (nr > 0) loop(count-1)
        else Task.eval(nr)
      }

    val task = loop(100)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  test("Task.apply.flatMap loops should work with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)

    def loop(count: Int): Task[Int] =
      Task(count).flatMap { nr =>
        if (nr > 0) loop(count-1)
        else Task(nr)
      }

    val task = loop(100)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(0)))
  }
}
