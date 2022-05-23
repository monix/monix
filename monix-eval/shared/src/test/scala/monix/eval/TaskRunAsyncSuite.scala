/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import scala.concurrent.duration._

object TaskRunAsyncSuite extends BaseTestSuite {
  test("runAsync") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val f = task.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(4)))
  }

  test("runAsync is cancelable") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).delayExecution(1.second)
    val f = task.runToFuture
    s.tick()

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(f.value, None)
    f.cancel(); s.tick()

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)
  }

  test("runAsync for Task.now(x)") { implicit s =>
    val f = Task.now(1).runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test("runAsync for Task.now(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val f = Task.now(1).runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test("runAsync for Task.raiseError(x)") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.raiseError(dummy).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("runAsync for Task.raiseError(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val dummy = DummyException("dummy")
    val f = Task.raiseError(dummy).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("runAsync(Callback)") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val p = Promise[Int]()
    task.runAsync(Callback.fromPromise(p))
    s.tick()
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("runAsync(Callback) is cancelable") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).delayExecution(1.second)
    val p = Promise[Int]()
    val f = task.runAsync(Callback.fromPromise(p))
    s.tick()

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(p.future.value, None)
    f.cancel(); s.tick()

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(p.future.value, None)
  }

  test("runAsync(Callback) for Task.now(x)") { implicit s =>
    val p = Promise[Int]()

    Task.now(1).runAsync(Callback.fromPromise(p))
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("runAsync(Callback) for Task.now(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val p = Promise[Int]()

    Task.now(1).runAsync(Callback.fromPromise(p))
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("runAsync(Callback) for Task.raiseError(x)") { implicit s =>
    val p = Promise[Int]()
    val dummy = DummyException("dummy")
    Task.raiseError(dummy).runAsync(Callback.fromPromise(p))
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("runAsync(Callback) for Task.raiseError(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val p = Promise[Int]()
    val dummy = DummyException("dummy")

    Task.raiseError(dummy).runAsync(Callback.fromPromise(p))
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("runAsyncF(Callback)") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val p = Promise[Int]()
    task.runAsyncF(Callback.fromPromise(p))
    s.tick()
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("runAsyncF(Callback) is cancelable") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).delayExecution(1.second)
    val p = Promise[Int]()
    val f = task.runAsyncF(Callback.fromPromise(p))
    s.tick()

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(p.future.value, None)
    f.runAsyncAndForget; s.tick()

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(p.future.value, None)
  }

  test("runAsyncF(Callback) for Task.now(x)") { implicit s =>
    val p = Promise[Int]()

    Task.now(1).runAsyncF(Callback.fromPromise(p))
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("runAsyncF(Callback) for Task.now(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val p = Promise[Int]()

    Task.now(1).runAsyncF(Callback.fromPromise(p))
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("runAsyncF(Callback) for Task.raiseError(x)") { implicit s =>
    val p = Promise[Int]()
    val dummy = DummyException("dummy")
    Task.raiseError(dummy).runAsyncF(Callback.fromPromise(p))
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("runAsyncF(Callback) for Task.raiseError(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val p = Promise[Int]()
    val dummy = DummyException("dummy")

    Task.raiseError(dummy).runAsyncF(Callback.fromPromise(p))
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("runAsyncUncancelable(Callback)") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val p = Promise[Int]()
    task.runAsyncUncancelable(Callback.fromPromise(p))
    s.tick()
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("runAsyncUncancelable(Callback) for Task.now(x)") { implicit s =>
    val p = Promise[Int]()

    Task.now(1).runAsyncUncancelable(Callback.fromPromise(p))
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("runAsyncUncancelable(Callback) for Task.now(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val p = Promise[Int]()

    Task.now(1).runAsyncUncancelable(Callback.fromPromise(p))
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("runAsyncUncancelable(Callback) for Task.raiseError(x)") { implicit s =>
    val p = Promise[Int]()
    val dummy = DummyException("dummy")
    Task.raiseError(dummy).runAsyncUncancelable(Callback.fromPromise(p))
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("runAsyncUncancelable(Callback) for Task.raiseError(x) with AlwaysAsyncExecution") { s =>
    implicit val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    val p = Promise[Int]()
    val dummy = DummyException("dummy")

    Task.raiseError(dummy).runAsyncUncancelable(Callback.fromPromise(p))
    assertEquals(p.future.value, None)

    s.tick()
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("runAsyncAndForget") { implicit s =>
    var effect = 0
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1).foreachL { i =>
      effect = i
    }
    task.runAsyncAndForget
    s.tick()
    assertEquals(effect, 4)
  }

  test("runAsyncAndForget for Task.now(x)") { implicit s =>
    val f = Task.now(1).runAsyncAndForget
    assertEquals(f, ())
  }

  test("runAsyncAndForget for Task.raiseError(x)") { implicit s =>
    val dummy = DummyException("dummy")
    Task.raiseError(dummy).runAsyncAndForget
    assertEquals(s.state.lastReportedError, dummy)
  }
}
