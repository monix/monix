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

import monix.execution.CancelableFuture
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import scala.concurrent.{ Promise, TimeoutException }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import monix.execution.atomic.Atomic

object TaskRaceSuite extends BaseTestSuite {
  test("Task.raceMany should switch to other") { implicit s =>
    val task =
      Task.raceMany(Seq(Task.evalAsync(1).delayExecution(10.seconds), Task.evalAsync(99).delayExecution(1.second)))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task.raceMany should onError from other") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.raceMany(
      Seq(Task.evalAsync(1).delayExecution(10.seconds), Task.evalAsync(throw ex).delayExecution(1.second))
    )
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.raceMany should mirror the source") { implicit s =>
    val task =
      Task.raceMany(Seq(Task.evalAsync(1).delayExecution(1.seconds), Task.evalAsync(99).delayExecution(10.second)))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.tasks.isEmpty, "other should be canceled")
  }

  test("Task.raceMany should onError from the source") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.raceMany(
      Seq(Task.evalAsync(throw ex).delayExecution(1.seconds), Task.evalAsync(99).delayExecution(10.second))
    )
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.tasks.isEmpty, "other should be canceled")
  }

  test("Task.raceMany should cancel both") { implicit s =>
    val task =
      Task.raceMany(Seq(Task.evalAsync(1).delayExecution(10.seconds), Task.evalAsync(99).delayExecution(1.second)))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "both should be canceled")
  }

  test("Task.raceMany should be stack safe, take 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.evalAsync(x))
    val sum = Task.raceMany(tasks)

    sum.runToFuture
    s.tick()
  }

  test("Task.raceMany should be stack safe, take 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val sum = Task.raceMany(tasks)

    sum.runToFuture
    s.tick()
  }

  test("Task.raceMany has a stack safe cancelable") { implicit sc =>
    val count = if (Platform.isJVM) 10000 else 1000
    val p = Promise[Int]()

    val tasks = (0 until count).map(_ => Task.never[Int])
    val all = tasks.foldLeft(Task.never[Int])((acc, t) => Task.raceMany(List(acc, t)))

    val f = Task.raceMany(List(Task.fromFuture(p.future), all)).runToFuture
    sc.tick()
    p.success(1)
    sc.tick()

    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#timeout should timeout") { implicit s =>
    val task = Task.evalAsync(1).delayExecution(10.seconds).timeout(1.second)
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assert(f.value.isDefined && f.value.get.failed.get.isInstanceOf[TimeoutException], "isInstanceOf[TimeoutException]")

    assert(s.state.tasks.isEmpty, "Main task was not canceled!")
  }

  test("Task#timeout should mirror the source in case of success") { implicit s =>
    val task = Task.evalAsync(1).delayExecution(1.seconds).timeout(10.second)
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should mirror the source in case of error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.evalAsync(throw ex).delayExecution(1.seconds).timeout(10.second)
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel both the source and the timer") { implicit s =>
    val task = Task.evalAsync(1).delayExecution(10.seconds).timeout(1.second)
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
  }

  test("Task#timeout with backup should timeout") { implicit s =>
    val task = Task.evalAsync(1).delayExecution(10.seconds).timeoutTo(1.second, Task.evalAsync(99))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#timeout with backup should mirror the source in case of success") { implicit s =>
    val task = Task.evalAsync(1).delayExecution(1.seconds).timeoutTo(10.second, Task.evalAsync(99))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout with backup should mirror the source in case of error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.evalAsync(throw ex).delayExecution(1.seconds).timeoutTo(10.second, Task.evalAsync(99))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel both the source and the timer") { implicit s =>
    val task = Task.evalAsync(1).delayExecution(10.seconds).timeoutTo(1.second, Task.evalAsync(99))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel the backup") { implicit s =>
    val task =
      Task.evalAsync(1).delayExecution(10.seconds).timeoutTo(1.second, Task.evalAsync(99).delayExecution(2.seconds))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.seconds)
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "backup should be canceled")
  }

  test("Task#timeout should not return the source after timeout") { implicit s =>
    val task =
      Task.evalAsync(1).delayExecution(2.seconds).timeoutTo(1.second, Task.evalAsync(99).delayExecution(2.seconds))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)

    s.tick(3.seconds)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#timeout should cancel the source after timeout") { implicit s =>
    val backup = Task.evalAsync(99).delayExecution(1.seconds)
    val task = Task.evalAsync(1).delayExecution(5.seconds).timeoutTo(1.second, backup)
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.seconds)
    assert(s.state.tasks.size == 1, "source should be canceled after timeout")

    s.tick(1.seconds)
    assert(s.state.tasks.isEmpty, "all task should be completed")
  }

  test("Task#timeoutL should evaluate as specified: lazy, no memoization") { implicit s =>
    val cnt = Atomic(0L)
    val timeout = Task(cnt.incrementAndGet().seconds)
    val loop = Task(10).delayExecution(2.9.seconds).timeoutL(timeout).onErrorRestart(3)
    val f = loop.runToFuture

    s.tick(1.second)
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)
    s.tick(3.seconds)
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task#timeoutL should evaluate as specified: lazy, with memoization") { implicit s =>
    val cnt = Atomic(0L)
    val timeout = Task.evalOnce(cnt.incrementAndGet().seconds)
    val loop = Task(10).delayExecution(10.seconds).timeoutL(timeout).onErrorRestart(3)
    val f = loop.runToFuture

    s.tick(1.second)
    assertEquals(f.value, None)

    s.tick(1.seconds)
    assertEquals(f.value, None)

    s.tick(1.seconds)
    assertEquals(f.value, None)

    s.tick(1.seconds)
    assert(f.value.isDefined)
    assert(f.value.get.isFailure)
    assert(f.value.get.failed.get.isInstanceOf[TimeoutException])
  }

  test("Task#timeoutL considers time taken to evaluate the duration task") { implicit s =>
    val timeout = Task(3.seconds).delayExecution(2.seconds)
    val f = Task(10).delayExecution(4.seconds).timeoutToL(timeout, Task(-10)).runToFuture

    s.tick(2.seconds)
    assertEquals(f.value, None)

    s.tick(1.seconds)
    assertEquals(f.value, Some(Success(-10)))
  }

  test("Task#timeoutL: evaluation time took > timeout => timeout is immediately completed") { implicit s =>
    val timeout = Task(2.seconds).delayExecution(3.seconds)
    val f = Task(10).delayExecution(4.seconds).timeoutToL(timeout, Task(-10)).runToFuture

    s.tick(3.seconds)
    assertEquals(f.value, Some(Success(-10)))
  }

  test("Task.racePair(a,b) should work if a completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.racePair(ta, tb).flatMap {
      case Left((a, taskB)) =>
        taskB.join.map(b => a + b)
      case Right((taskA, b)) =>
        taskA.join.map(a => a + b)
    }

    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(30)))
  }

  test("Task.racePair(a,b) should cancel both") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.racePair(ta, tb)
    val f = t.runToFuture
    s.tick()
    f.cancel()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.racePair(A,B) should not cancel B if A completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)
    var future = Option.empty[CancelableFuture[Int]]

    val t = Task.racePair(ta, tb).map {
      case Left((a, taskB)) =>
        future = Some(taskB.join.runToFuture)
        a
      case Right((taskA, b)) =>
        future = Some(taskA.join.runToFuture)
        b
    }

    val f = t.runToFuture
    s.tick(1.second)
    f.cancel()

    assertEquals(f.value, Some(Success(10)))
    assert(future.isDefined, "future.isDefined")
    assertEquals(future.flatMap(_.value), None)

    s.tick(1.second)
    assertEquals(future.flatMap(_.value), Some(Success(20)))
  }

  test("Task.racePair(A,B) should not cancel A if B completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)
    var future = Option.empty[CancelableFuture[Int]]

    val t = Task.racePair(ta, tb).map {
      case Left((a, taskB)) =>
        future = Some(taskB.join.runToFuture)
        a
      case Right((taskA, b)) =>
        future = Some(taskA.join.runToFuture)
        b
    }

    val f = t.runToFuture
    s.tick(1.second)
    f.cancel()

    assertEquals(f.value, Some(Success(20)))
    assert(future.isDefined, "future.isDefined")
    assertEquals(future.flatMap(_.value), None)

    s.tick(1.second)
    assertEquals(future.flatMap(_.value), Some(Success(10)))
  }

  test("Task.racePair(A,B) should end both in error if A completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.racePair(ta, tb)
    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.racePair(A,B) should end both in error if B completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(2.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(1.second)

    val t = Task.racePair(ta, tb)
    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.racePair(A,B) should work if A completes second in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t1 = Task.racePair(ta, tb).flatMap {
      case Left((a, taskB)) =>
        taskB.join.map(b => a + b)
      case Right((taskA, b)) =>
        taskA.join.map(a => a + b)
    }

    val t2 = Task.racePair(ta, tb).map {
      case Left((a, _)) => a
      case Right((_, b)) => b
    }

    val f1 = t1.runToFuture
    val f2 = t2.runToFuture
    s.tick(2.seconds)

    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Success(20)))
  }

  test("Task.racePair(A,B) should work if B completes second in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(1.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(2.second)

    val t1 = Task.racePair(ta, tb).flatMap {
      case Left((a, taskB)) =>
        taskB.join.map(b => a + b)
      case Right((taskA, b)) =>
        taskA.join.map(a => a + b)
    }

    val t2 = Task.racePair(ta, tb).map {
      case Left((a, _)) => a
      case Right((_, b)) => b
    }

    val f1 = t1.runToFuture
    val f2 = t2.runToFuture
    s.tick(2.seconds)

    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Success(10)))
  }

  test("Task.racePair should be stack safe, take 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.evalAsync(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc, t) =>
      Task.racePair(acc, t).map {
        case Left((a, _)) => a
        case Right((_, b)) => b
      }
    )

    sum.runToFuture
    s.tick()
  }

  test("Task.racePair should be stack safe, take 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc, t) =>
      Task.racePair(acc, t).map {
        case Left((a, _)) => a
        case Right((_, b)) => b
      }
    )

    sum.runToFuture
    s.tick()
  }

  test("Task.racePair has a stack safe cancelable") { implicit sc =>
    val count = if (Platform.isJVM) 10000 else 1000
    val p = Promise[Int]()

    val tasks = (0 until count).map(_ => Task.never[Int])
    val all = tasks.foldLeft(Task.never[Int])((acc, t) =>
      Task.racePair(acc, t).flatMap {
        case Left((a, fb)) => fb.cancel.map(_ => a)
        case Right((fa, b)) => fa.cancel.map(_ => b)
      }
    )

    val f = Task
      .racePair(Task.fromFuture(p.future), all)
      .flatMap {
        case Left((a, fb)) => fb.cancel.map(_ => a)
        case Right((fa, b)) => fa.cancel.map(_ => b)
      }
      .runToFuture

    sc.tick()
    p.success(1)
    sc.tick()

    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.race(a, b) should work if a completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Success(10)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should work if b completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Success(20)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should cancel both") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.race(ta, tb)
    val f = t.runToFuture
    s.tick()
    f.cancel()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should end both in error if `a` completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.race(ta, tb)
    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should end both in error if `b` completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(20).delayExecution(2.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(1.second)

    val t = Task.race(ta, tb)
    val f = t.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should work if `a` completes in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(2.second).uncancelable
    val tb = Task.now(20).delayExecution(1.seconds)

    val task = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = task.runToFuture
    s.tick(2.seconds)

    assertEquals(f.value, Some(Success(20)))
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.race(a, b) should work if `b` completes in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(20).delayExecution(1.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(2.second).uncancelable

    val task = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = task.runToFuture
    s.tick(2.seconds)

    assertEquals(f.value, Some(Success(20)))
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.race should be stack safe, take 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.evalAsync(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc, t) =>
      Task.race(acc, t).map {
        case Left(a) => a
        case Right(b) => b
      }
    )

    sum.runToFuture
    s.tick()
  }

  test("Task.race should be stack safe, take 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc, t) =>
      Task.race(acc, t).map {
        case Left(a) => a
        case Right(b) => b
      }
    )

    sum.runToFuture
    s.tick()
  }

  test("Task.race has a stack safe cancelable") { implicit sc =>
    val count = if (Platform.isJVM) 10000 else 1000
    val p = Promise[Int]()

    val tasks = (0 until count).map(_ => Task.never[Int])
    val all = tasks.foldLeft(Task.never[Int])((acc, t) =>
      Task.race(acc, t).map {
        case Left(a) => a
        case Right(b) => b
      }
    )

    val f = Task.race(Task.fromFuture(p.future), all).map { case Left(a) => a; case Right(b) => b }.runToFuture

    sc.tick()
    p.success(1)
    sc.tick()

    assertEquals(f.value, Some(Success(1)))
  }
}
