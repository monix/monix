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

import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.concurrent.duration._
import scala.util.Success

object TaskStartAndForgetSuite extends BaseTestSuite {

  test("Task.startAndForget triggers execution in background thread") { implicit sc =>
    var counter = 0
    val task = Task.eval { counter += 1; counter }

    val main = for {
      _ <- task.delayExecution(1.second).startAndForget
      _ <- task.delayExecution(1.second).startAndForget
    } yield ()

    val f = main.runToFuture
    assertEquals(f.value, Some(Success(())))
    assertEquals(counter, 0)

    sc.tick(1.second)
    assertEquals(counter, 2)
  }

  test("Task.startAndForget triggers exceptions in background thread") { implicit sc =>
    val dummy = new DummyException()
    val task = Task.now(20)
    val errorTask = Task.raiseError(dummy)

    val result = for {
      _     <- errorTask.startAndForget
      value <- task
    } yield value

    val f = result.runToFuture
    sc.tick()
    assertEquals(f.value, Some(Success(20)))
    assertEquals(sc.state.lastReportedError, dummy)
  }

  test("Task.startAndForget is stack safe") { implicit sc =>
    val count = if (Platform.isJVM) 100000 else 5000

    var task: Task[Any] = Task.evalAsync(1)
    for (_ <- 0 until count) task = task.startAndForget
    for (_ <- 0 until count) task = task.flatMap(_ => Task.unit)

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, Some(Success(())))
  }

}
