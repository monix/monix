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
import monix.execution.atomic.AtomicInt
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import scala.concurrent.Promise
import scala.util.{ Failure, Success }
import concurrent.duration._

object TaskMemoizeOnSuccessSuite extends BaseTestSuite {
  test("Task.memoizeOnSuccess should work asynchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.memoizeOnSuccess
      .flatMap(Task.now)
      .flatMap(Task.now)

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.memoizeOnSuccess
      .flatMap(Task.now)
      .flatMap(Task.now)

    task.runToFuture
    s.tick()

    val f1 = task.runToFuture
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runToFuture
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.evalAsync(1)
    for (i <- 0 until count) task = task.memoizeOnSuccess

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.flatMap.memoizeOnSuccess should be stack safe, test 1") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.evalAsync(1)

    for (i <- 0 until count) {
      task = task.memoizeOnSuccess.flatMap(x => Task.now(x))
    }

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.flatMap.memoizeOnSuccess should be stack safe, test 2") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.evalAsync(1)
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess.flatMap(x => Task.evalAsync(x))
    }

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalAsync(error).memoizeOnSuccess should not be idempotent") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task[Int] { effect += 1; throw dummy }.memoizeOnSuccess
      .flatMap(Task.now)
      .flatMap(Task.now)

    val f1 = task.runToFuture; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val f2 = task.runToFuture; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("Task.memoizeOnSuccess.materialize") { implicit s =>
    val f = Task.evalAsync(10).memoizeOnSuccess.materialize.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(Success(10))))
  }

  test("Task.evalAsync(error).memoizeOnSuccess.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task[Int](throw dummy).memoizeOnSuccess.materialize.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.eval.memoizeOnSuccess should work for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.eval { effect += 1; effect }.memoizeOnSuccess

    val f = task.runToFuture; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.eval.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.eval { effect += 1; effect }.memoizeOnSuccess
    task.runToFuture
    s.tick()

    val f1 = task.runToFuture
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runToFuture
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.eval(error).memoizeOnSuccess should not be idempotent") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.eval[Int] { effect += 1; throw dummy }.memoizeOnSuccess

    val f1 = task.runToFuture; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val f2 = task.runToFuture; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("Task.eval.memoizeOnSuccess") { implicit s =>
    var effect = 0
    val task = Task.eval { effect += 1; effect }.memoizeOnSuccess

    val r1 = task.runToFuture
    val r2 = task.runToFuture
    val r3 = task.runToFuture

    s.tickOne()
    assertEquals(r1.value, Some(Success(1)))
    assertEquals(r2.value, Some(Success(1)))
    assertEquals(r3.value, Some(Success(1)))
  }

  test("Task.eval.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count)
      task = task.memoizeOnSuccess

    val f = task.runToFuture; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.eval.flatMap.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess.flatMap(x => Task.eval(x))
    }

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.defer(evalAlways).memoizeOnSuccess") { implicit s =>
    var effect = 0
    val task = Task.defer(Task.eval { effect += 1; effect }).memoizeOnSuccess

    val r1 = task.runToFuture
    val r2 = task.runToFuture
    val r3 = task.runToFuture

    s.tick()
    assertEquals(r1.value, Some(Success(1)))
    assertEquals(r2.value, Some(Success(1)))
    assertEquals(r3.value, Some(Success(1)))
  }

  test("Task.evalOnce.memoizeOnSuccess should work for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.evalOnce { effect += 1; effect }.memoizeOnSuccess

    val f = task.runToFuture; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalOnce.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.evalOnce { effect += 1; effect }.memoizeOnSuccess
    task.runToFuture
    s.tick()

    val f1 = task.runToFuture
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runToFuture
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.evalOnce(error).memoizeOnSuccess should work") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")
    val task = Task.evalOnce[Int] { effect += 1; throw dummy }.memoizeOnSuccess

    val f1 = task.runToFuture; s.tick()
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val f2 = task.runToFuture; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  test("Task.evalOnce.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess
    }

    val f = task.runToFuture; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.evalOnce.flatMap.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.eval(1)
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess.flatMap(x => Task.evalOnce(x))
    }

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.memoizeOnSuccess should work synchronously for first subscriber") { implicit s =>
    var effect = 0
    val task = Task.now { effect += 1; effect }.memoizeOnSuccess

    val f = task.runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.memoizeOnSuccess should work synchronously for next subscribers") { implicit s =>
    var effect = 0
    val task = Task.now { effect += 1; effect }.memoizeOnSuccess

    task.runToFuture
    s.tick()

    val f1 = task.runToFuture
    assertEquals(f1.value, Some(Success(1)))
    val f2 = task.runToFuture
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.error.memoizeOnSuccess should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy).memoizeOnSuccess

    val f1 = task.runToFuture
    assertEquals(f1.value, Some(Failure(dummy)))
    val f2 = task.runToFuture
    assertEquals(f2.value, Some(Failure(dummy)))
  }

  test("Task.now.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.now(1)
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess
    }

    val f = task.runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.now.flatMap.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.now(1)
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess.flatMap(x => Task.now(x))
    }

    val f = task.runToFuture
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.suspend.memoizeOnSuccess should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 50000 else 5000
    var task = Task.defer(Task.now(1))
    for (i <- 0 until count) {
      task = task.memoizeOnSuccess.map(x => x)
    }

    val f = task.runToFuture; s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.memoizeOnSuccess effects, sequential") { implicit s =>
    var effect = 0
    val task1 = Task.evalAsync { effect += 1; 3 }.memoizeOnSuccess
    val task2 = task1.map { x =>
      effect += 1; x + 1
    }

    val result1 = task2.runToFuture; s.tick()
    assertEquals(effect, 2)
    assertEquals(result1.value, Some(Success(4)))

    val result2 = task2.runToFuture; s.tick()
    assertEquals(effect, 3)
    assertEquals(result2.value, Some(Success(4)))
  }

  test("Task.memoizeOnSuccess effects, parallel") { implicit s =>
    var effect = 0
    val task1 = Task.evalAsync { effect += 1; 3 }.memoizeOnSuccess
    val task2 = task1.map { x =>
      effect += 1; x + 1
    }

    val result1 = task2.runToFuture
    val result2 = task2.runToFuture

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
    val task2 = task1.map { x =>
      effect += 1; x + 1
    }

    val result1 = task2.runToFuture; s.tick()
    assertEquals(effect, 2)
    assertEquals(result1.value, Some(Success(4)))

    val result2 = task2.runToFuture; s.tick()
    assertEquals(effect, 3)
    assertEquals(result2.value, Some(Success(4)))
  }

  test("Task.suspend.flatMap.memoizeOnSuccess effects") { implicit s =>
    var effect = 0
    val task1 = Task.defer { effect += 1; Task.now(2) }
      .flatMap(x => Task.now(x + 1))
      .memoizeOnSuccess
    val task2 = task1.map { x =>
      effect += 1; x + 1
    }

    val result1 = task2.runToFuture; s.tick()
    assertEquals(effect, 2)
    assertEquals(result1.value, Some(Success(4)))

    val result2 = task2.runToFuture; s.tick()
    assertEquals(effect, 3)
    assertEquals(result2.value, Some(Success(4)))

    val result3 = task2.runToFuture; s.tick()
    assertEquals(effect, 4)
    assertEquals(result3.value, Some(Success(4)))
  }

  test("Task.memoizeOnSuccess should make subsequent subscribers wait for the result, as future") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess

    val first = task.runToFuture
    s.tick()
    assertEquals(first.value, None)

    val second = task.runToFuture
    val third = task.runToFuture

    s.tick()
    assertEquals(second.value, None)
    assertEquals(third.value, None)

    s.tick(1.second)
    assertEquals(first.value, Some(Success(2)))
    assertEquals(second.value, Some(Success(2)))
    assertEquals(third.value, Some(Success(2)))
  }

  test("Task.memoizeOnSuccess should make subsequent subscribers wait for the result, as callback") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess

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
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess

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

  test("Task.memoizeOnSuccess should be cancellable (future)") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess

    val first = task.runToFuture
    s.tick()
    assertEquals(first.value, None)

    val second = task.runToFuture
    val third = task.runToFuture

    s.tick()
    assertEquals(second.value, None)
    assertEquals(third.value, None)

    third.cancel()
    s.tick()
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")

    s.tick(1.second)
    assertEquals(first.value, Some(Success(2)))
    assertEquals(second.value, Some(Success(2)))
    assertEquals(third.value, None)
    assertEquals(effect, 1)
  }

  test("Task.memoizeOnSuccess should be cancellable (callback #1)") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess

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
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")

    s.tick(1.second)
    assertEquals(first.future.value, Some(Success(2)))
    assertEquals(second.future.value, Some(Success(2)))
    assertEquals(third.future.value, None)
    assertEquals(effect, 1)
  }

  test("Task.memoizeOnSuccess should be cancellable (callback #2)") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess.map(x => x)

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
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")

    s.tick(1.second)
    assertEquals(first.future.value, Some(Success(2)))
    assertEquals(second.future.value, Some(Success(2)))
    assertEquals(third.future.value, None)
    assertEquals(effect, 1)
  }

  test("Task.memoizeOnSuccess should not be cancelable") { implicit s =>
    var effect = 0
    val task = Task.evalAsync { effect += 1; effect }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess
    val first = task.runToFuture
    val second = task.runToFuture

    s.tick()
    assertEquals(first.value, None)
    assertEquals(second.value, None)
    first.cancel()

    s.tick()
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(first.value, None)
    assertEquals(second.value, None)
    assertEquals(effect, 0)

    // -- Second wave:
    val third = task.runToFuture
    val fourth = task.runToFuture

    s.tick(1.second)
    assertEquals(first.value, None)
    assertEquals(second.value, Some(Success(2)))
    assertEquals(third.value, Some(Success(2)))
    assertEquals(fourth.value, Some(Success(2)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.evalAsync(error).memoizeOnSuccess can register multiple listeners") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val task = Task[Int] { effect += 1; throw dummy }.delayExecution(1.second).map(_ + 1).memoizeOnSuccess

    val first = task.runToFuture
    s.tick()
    assertEquals(first.value, None)
    assertEquals(effect, 0)

    val second = task.runToFuture
    val third = task.runToFuture

    s.tick()
    assertEquals(second.value, None)
    assertEquals(third.value, None)

    s.tick(1.second)
    assertEquals(first.value, Some(Failure(dummy)))
    assertEquals(second.value, Some(Failure(dummy)))
    assertEquals(third.value, Some(Failure(dummy)))
    assertEquals(effect, 1)

    val fourth = task.runToFuture
    val fifth = task.runToFuture
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
    val task = Task.eval(1).map(_ + 1).memoize
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

  testAsync("local.write.memoizeOnSuccess works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      local <- TaskLocal(0)
      memoizeTask = local.write(100).flatMap(_ => local.read).memoizeOnSuccess
      v1 <- memoizeTask
      _  <- Task.shift
      v2 <- local.read
      _  <- local.write(200)
      _  <- memoizeTask
      _  <- Task.shift
      v3 <- local.read
    } yield (v1, v2, v3)

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, (100, 100, 200))
    }
  }

  testAsync("Task.memoizeOnSuccess doesn't corrupt Local (issue #856)") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val memoizedTask = Task(5).memoizeOnSuccess

    val i = AtomicInt(0)

    val t = for {
      local <- TaskLocal(0)
      ii    <- Task.evalAsync(i.incrementAndGet())
      _     <- local.write(ii)
      result <- Task.parZip2(
        memoizedTask.flatMap { _ =>
          local.read.executeAsync
        },
        memoizedTask.flatMap { _ =>
          local.read.executeAsync
        }
      )
    } yield result

    val f1 = t.runToFutureOpt
    val f2 = t.runToFutureOpt
    val f3 = t.runToFutureOpt

    for {
      v1 <- f1
      v2 <- f2
      v3 <- f3
    } yield {
      assertEquals(List(v1, v2, v3).sortBy(_._1), List((1, 1), (2, 2), (3, 3)))
    }
  }
}
