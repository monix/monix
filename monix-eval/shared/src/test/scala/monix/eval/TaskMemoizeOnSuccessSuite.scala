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


import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object TaskMemoizeOnSuccessSuite extends BaseTestSuite {
  test("Task.apply.memoizeOnSuccess should work asynchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task { effect += 1; effect }.memoizeOnSuccess
      .flatMap(Task.now).flatMap(Task.now)

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task { effect += 1; effect }.memoizeOnSuccess
      .flatMap(Task.now).flatMap(Task.now)

    task.runAsync
    s.tick()

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.apply.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.flatMap.memoizeOnSuccess should be stack safe, test 1") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess.flatMap(x => Task.now(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.flatMap.memoizeOnSuccess should be stack safe, test 2") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess.flatMap(x => Task(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply(error).memoizeOnSuccess should not be idempotent") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.apply[Int] { effect += 1; throw dummy }.memoizeOnSuccess
      .flatMap(Task.now).flatMap(Task.now)

    val f1 = task.runAsync; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val f2 = task.runAsync; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("Task.apply.memoizeOnSuccess.materialize") { implicit s =>
    val f = Task(10).memoizeOnSuccess.materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Success(10))))
  }

  test("Task.apply(error).memoizeOnSuccess.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task[Int](throw dummy).memoizeOnSuccess.materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.eval.memoizeOnSuccess should work for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.eval { effect += 1; effect }.memoizeOnSuccess

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.eval.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.eval { effect += 1; effect }.memoizeOnSuccess
    task.runAsync
    s.tick()

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.eval(error).memoizeOnSuccess should not be idempotent") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.eval[Int] { effect += 1; throw dummy }.memoizeOnSuccess

    val f1 = task.runAsync; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val f2 = task.runAsync; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("Task.eval.memoizeOnSuccess") { implicit s =>
    var effect = 0
    val task = Task.eval { effect += 1; effect }.memoizeOnSuccess

    val r1 = task.runAsync
    val r2 = task.runAsync
    val r3 = task.runAsync

    s.tickOne()
    assertEquals(r1.value, Some(Success(1)))
    assertEquals(r2.value, Some(Success(1)))
    assertEquals(r3.value, Some(Success(1)))
  }

  test("Task.eval.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.eval.flatMap.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess.flatMap(x => Task.eval(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.defer(evalAlways).memoizeOnSuccess") { implicit s =>
    var effect = 0
    val task = Task.defer(Task.eval { effect += 1; effect }).memoizeOnSuccess

    val r1 = task.runAsync
    val r2 = task.runAsync
    val r3 = task.runAsync

    s.tick()
    assertEquals(r1.value, Some(Success(1)))
    assertEquals(r2.value, Some(Success(1)))
    assertEquals(r3.value, Some(Success(1)))
  }

  test("Task.evalOnce.memoizeOnSuccess should work for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.evalOnce { effect += 1; effect }.memoizeOnSuccess

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalOnce.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.evalOnce { effect += 1; effect }.memoizeOnSuccess
    task.runAsync
    s.tick()

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.evalOnce(error).memoizeOnSuccess should work") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.evalOnce[Int] { effect += 1; throw dummy }.memoizeOnSuccess

    val f1 = task.runAsync; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val f2 = task.runAsync; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  test("Task.evalOnce.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalOnce.flatMap.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess.flatMap(x => Task.evalOnce(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.memoizeOnSuccess should work synchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.now { effect += 1; effect }.memoizeOnSuccess

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.now { effect += 1; effect }.memoizeOnSuccess
    task.runAsync
    s.tick()

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.error.memoizeOnSuccess should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy).memoizeOnSuccess

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Failure(dummy)))
    val f2 = task.runAsync
    assertEquals(f2.value, Some(Failure(dummy)))
  }

  test("Task.now.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.now(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.flatMap.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.now(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess.flatMap(x => Task.now(x))

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.suspend.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.defer(Task.now(1))
    for (i <- 0 until count) task = task.memoizeOnSuccess.map(x => x)

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.apply.memoizeOnSuccess effects, sequential") { implicit s =>
    var effect = 0
    val task1 = Task { effect += 1; 3 }.memoizeOnSuccess
    val task2 = task1.map { x => effect += 1; x + 1 }

    val result1 = task2.runAsync; s.tick()
    assertEquals(effect, 2)
    assertEquals(result1.value, Some(Success(4)))

    val result2 = task2.runAsync; s.tick()
    assertEquals(effect, 3)
    assertEquals(result2.value, Some(Success(4)))
  }

  test("Task.apply.memoizeOnSuccess effects, parallel") { implicit s =>
    var effect = 0
    val task1 = Task { effect += 1; 3 }.memoizeOnSuccess
    val task2 = task1.map { x => effect += 1; x + 1 }

    val result1 = task2.runAsync
    val result2 = task2.runAsync

    assertEquals(result1.value, None)
    assertEquals(result2.value, None)

    s.tick()
    assertEquals(effect, 3)
    assertEquals(result1.value, Some(Success(4)))
    assertEquals(result2.value, Some(Success(4)))
  }

  test("Task.suspend.memoizeOnSuccess effects") { implicit s =>
    var effect = 0
    val task1 = Task.defer { effect += 1; Task.now(3) }.memoizeOnSuccess
    val task2 = task1.map { x => effect += 1; x + 1 }

    val result1 = task2.runAsync; s.tick()
    assertEquals(effect, 2)
    assertEquals(result1.value, Some(Success(4)))

    val result2 = task2.runAsync; s.tick()
    assertEquals(effect, 3)
    assertEquals(result2.value, Some(Success(4)))
  }

  test("Task.suspend.flatMap.memoizeOnSuccess effects") { implicit s =>
    var effect = 0
    val task1 = Task.defer { effect += 1; Task.now(2) }
      .flatMap(x => Task.now(x + 1)).memoizeOnSuccess
    val task2 = task1.map { x => effect += 1; x + 1 }

    val result1 = task2.runAsync; s.tick()
    assertEquals(effect, 2)
    assertEquals(result1.value, Some(Success(4)))

    val result2 = task2.runAsync; s.tick()
    assertEquals(effect, 3)
    assertEquals(result2.value, Some(Success(4)))

    val result3 = task2.runAsync; s.tick()
    assertEquals(effect, 4)
    assertEquals(result3.value, Some(Success(4)))
  }

  test("Task.memoizeOnSuccess should make subsequent subscribers wait for the result, as future") { implicit s =>
    import concurrent.duration._

    var effect = 0
    val task = Task { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = task.runAsync

    s.tick()
    assertEquals(first.value, None)

    val second = task.runAsync
    val third = task.runAsync

    s.tick()
    assertEquals(second.value, None)
    assertEquals(third.value, None)

    s.tick(1.second)
    assertEquals(first.value, Some(Success(2)))
    assertEquals(second.value, Some(Success(2)))
    assertEquals(third.value, Some(Success(2)))
  }

  test("Task.memoizeOnSuccess should make subsequent subscribers wait for the result, as callback") { implicit s =>
    import concurrent.duration._

    var effect = 0
    val task = Task { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = Promise[Int]()
    task.runAsync(Callback.fromPromise(first))

    s.tick()
    assertEquals(first.future.value, None)

    val second = Promise[Int](); task.runAsync(Callback.fromPromise(second))
    val third = Promise[Int](); task.runAsync(Callback.fromPromise(third))

    s.tick()
    assertEquals(second.future.value, None)
    assertEquals(third.future.value, None)

    s.tick(1.second)
    assertEquals(first.future.value, Some(Success(2)))
    assertEquals(second.future.value, Some(Success(2)))
    assertEquals(third.future.value, Some(Success(2)))
  }

  test("Task.memoizeOnSuccess should be synchronous for subsequent subscribers, as callback") { implicit s =>
    import concurrent.duration._

    var effect = 0
    val task = Task { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = Promise[Int]()
    task.runAsync(Callback.fromPromise(first))

    s.tick()
    assertEquals(first.future.value, None)

    s.tick(1.second)
    assertEquals(first.future.value, Some(Success(2)))

    val second = Promise[Int](); task.runAsync(Callback.fromPromise(second))
    val third = Promise[Int](); task.runAsync(Callback.fromPromise(third))
    assertEquals(second.future.value, Some(Success(2)))
    assertEquals(third.future.value, Some(Success(2)))
  }

  test("Task.memoizeOnSuccess should be cancelable for subsequent subscribers, as future") { implicit s =>
    import concurrent.duration._

    var effect = 0
    val task = Task { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = task.runAsync

    s.tick()
    assertEquals(first.value, None)

    val second = task.runAsync
    val third = task.runAsync

    s.tick()
    assertEquals(second.value, None)
    assertEquals(third.value, None)

    third.cancel()
    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(1.second)
    assertEquals(first.value, None)
    assertEquals(second.value, None)
    assertEquals(third.value, None)
    assertEquals(effect, 0)
  }

  test("Task.memoizeOnSuccess should be cancelable for subsequent subscribers, as callback, test 1") { implicit s =>
    import concurrent.duration._

    var effect = 0
    val task = Task { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = Promise[Int]()
    task.runAsync(Callback.fromPromise(first))

    s.tick()
    assertEquals(first.future.value, None)

    val second = Promise[Int]()
    val c2 = task.runAsync(Callback.fromPromise(second))
    val third = Promise[Int]()
    val c3 = task.runAsync(Callback.fromPromise(third))

    s.tick()
    assertEquals(second.future.value, None)
    assertEquals(third.future.value, None)

    c3.cancel()
    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(1.second)
    assertEquals(first.future.value, None)
    assertEquals(second.future.value, None)
    assertEquals(third.future.value, None)
    assertEquals(effect, 0)
  }

  test("Task.memoizeOnSuccess should be cancelable for subsequent subscribers, as callback, test 2") { implicit s =>
    import concurrent.duration._

    var effect = 0
    val task = Task { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess.map(x => x)
    val first = Promise[Int]()
    task.runAsync(Callback.fromPromise(first))

    s.tick()
    assertEquals(first.future.value, None)

    val second = Promise[Int]()
    task.runAsync(Callback.fromPromise(second))
    val third = Promise[Int]()
    val c3 = task.runAsync(Callback.fromPromise(third))

    s.tick()
    assertEquals(second.future.value, None)
    assertEquals(third.future.value, None)

    c3.cancel()
    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(1.second)
    assertEquals(first.future.value, None)
    assertEquals(second.future.value, None)
    assertEquals(third.future.value, None)
    assertEquals(effect, 0)
  }

  test("Task.apply(error).memoizeOnSuccess can register multiple listeners") { implicit s =>
    import concurrent.duration._

    val dummy = DummyException("dummy")
    var effect = 0

    val task = Task[Int] { effect += 1; throw dummy }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = task.runAsync

    s.tick()
    assertEquals(first.value, None)
    assertEquals(effect, 0)

    val second = task.runAsync
    val third = task.runAsync

    s.tick()
    assertEquals(second.value, None)
    assertEquals(third.value, None)

    s.tick(1.second)
    assertEquals(first.value, Some(Failure(dummy)))
    assertEquals(second.value, Some(Failure(dummy)))
    assertEquals(third.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val fourth = task.runAsync
    val fifth = task.runAsync
    s.tick()
    assertEquals(fourth.value, None)
    assertEquals(fifth.value, None)

    s.tick(1.second)
    assertEquals(fourth.value, Some(Failure(dummy)))
    assertEquals(fifth.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("Task.evalOnce eq Task.evalOnce.memoizeOnSuccess") { implicit s =>
    val task = Task.evalOnce(1)
    assertEquals(task, task.memoizeOnSuccess)
  }

  test("Task.eval.memoizeOnSuccess eq Task.eval.memoizeOnSuccess.memoizeOnSuccess") { implicit s =>
    val task = Task.eval(1).memoizeOnSuccess
    assertEquals(task, task.memoizeOnSuccess)
  }

  test("Task.eval.memoize eq Task.eval.memoize.memoizeOnSuccess") { implicit s =>
    val task = Task.eval(1).memoize
    assertEquals(task, task.memoizeOnSuccess)
  }

  test("Task.eval.map.memoize eq Task.eval.map.memoize.memoizeOnSuccess") { implicit s =>
    val task = Task.eval(1).map(_+1).memoize
    assertEquals(task, task.memoizeOnSuccess)
  }

  test("Task.now.memoizeOnSuccess eq Task.now") { implicit s =>
    val task = Task.now(1)
    assertEquals(task, task.memoizeOnSuccess)
  }

  test("Task.raiseError.memoizeOnSuccess eq Task.raiseError") { implicit s =>
    val task = Task.raiseError(DummyException("dummy"))
    assertEquals(task, task.memoizeOnSuccess)
  }
}
