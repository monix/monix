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

import monix.execution.Callback
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.exceptions.DummyException
import scala.concurrent.Promise
import scala.util.{ Failure, Success }

class TaskOverloadsSuite extends BaseTestSuite {
  fixture.test("Now.runAsync(scheduler)") { implicit s =>
    val task = Task.now(1)
    val f = task.runToFuture(s)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("Now.runAsync(callback)") { implicit s =>
    val task = Task.now(1)
    val p = Promise[Int]()
    task.runAsync(Callback.fromPromise(p))(s)
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("Now.runAsync(callback) for AlwaysAsyncExecution") { s =>
    val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val task = Task.now(1)
    val p = Promise[Int]()

    task.runAsync(Callback.fromPromise(p))(s2)
    assertEquals(p.future.value, None)

    s2.tick()
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("Now.runAsyncOpt(scheduler)") { implicit s =>
    val task = Task.now(1)
    val f = task.runToFutureOpt(s, Task.defaultOptions)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("Now.runAsyncOpt(callback)") { implicit s =>
    val task = Task.now(1)
    val p = Promise[Int]()
    task.runAsyncOpt(Callback.fromPromise(p))(s, Task.defaultOptions)
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("Error.runAsync(scheduler)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)
    val f = task.runToFuture(s)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("Error.runAsync(callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)
    val p = Promise[Int]()
    task.runAsync(Callback.fromPromise(p))(s)
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  fixture.test("Error.runAsync(callback) for AlwaysAsyncExecution") { s =>
    val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)
    val p = Promise[Int]()

    task.runAsync(Callback.fromPromise(p))(s2)
    assertEquals(p.future.value, None)

    s2.tick()
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  fixture.test("Error.runAsyncOpt(scheduler)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)
    val f = task.runToFutureOpt(s, Task.defaultOptions)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("Error.runAsyncOpt(callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)
    val p = Promise[Int]()
    task.runAsyncOpt(Callback.fromPromise(p))(s, Task.defaultOptions)
    assertEquals(p.future.value, Some(Failure(dummy)))
  }
}
