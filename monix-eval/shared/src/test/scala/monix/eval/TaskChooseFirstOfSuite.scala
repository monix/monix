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

package monix.eval

import monix.execution.CancelableFuture

import scala.util.{Failure, Success}
import concurrent.duration._
import scala.concurrent.TimeoutException

object TaskChooseFirstOfSuite extends BaseTestSuite {
  test("Task.chooseFirstOfList should switch to other") { implicit s =>
    val task = Task.chooseFirstOfList(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task.chooseFirstOfList should onError from other") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.chooseFirstOfList(Seq(Task(1).delayExecution(10.seconds), Task(throw ex).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.chooseFirstOfList should mirror the source") { implicit s =>
    val task = Task.chooseFirstOfList(Seq(Task(1).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task.chooseFirstOfList should onError from the source") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.chooseFirstOfList(Seq(Task(throw ex).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task.chooseFirstOfList should cancel both") { implicit s =>
    val task = Task.chooseFirstOfList(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "both should be canceled")
  }

  test("Task#timeout should timeout") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeout(1.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assert(f.value.isDefined && f.value.get.failed.get.isInstanceOf[TimeoutException],
      "isInstanceOf[TimeoutException]")
  }

  test("Task#timeout should mirror the source in case of success") { implicit s =>
    val task = Task(1).delayExecution(1.seconds).timeout(10.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.get.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should mirror the source in case of error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex).delayExecution(1.seconds).timeout(10.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.get.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel both the source and the timer") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeout(1.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
  }

  test("Task#timeout with backup should timeout") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeoutTo(1.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#timeout with backup should mirror the source in case of success") { implicit s =>
    val task = Task(1).delayExecution(1.seconds).timeoutTo(10.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.get.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout with backup should mirror the source in case of error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex).delayExecution(1.seconds).timeoutTo(10.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.get.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel both the source and the timer") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeoutTo(1.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel the backup") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeoutTo(1.second, Task(99).delayExecution(2.seconds))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.seconds)
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "backup should be canceled")
  }

  test("Task.chooseFirstOf(a,b) should work if a completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.chooseFirstOf(ta, tb).flatMap {
      case Left((a, futureB)) =>
        Task.fromFuture(futureB).map(b => a + b)
      case Right((futureA, b)) =>
        Task.fromFuture(futureA).map(a => a + b)
    }

    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(30)))
  }

  test("Task.chooseFirstOf(a,b) should cancel both") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.chooseFirstOf(ta, tb)
    val f = t.runAsync
    s.tick()
    f.cancel()
    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.chooseFirstOf(A,B) should not cancel B if A completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)
    var future = Option.empty[CancelableFuture[Int]]

    val t = Task.chooseFirstOf(ta, tb).map {
      case Left((a, futureB)) =>
        future = Some(futureB)
        a
      case Right((futureA, b)) =>
        future = Some(futureA)
        b
    }

    val f = t.runAsync
    s.tick(1.second)
    f.cancel()

    assertEquals(f.value, Some(Success(10)))
    assert(future.isDefined, "future.isDefined")
    assertEquals(future.flatMap(_.value), None)

    s.tick(1.second)
    assertEquals(future.flatMap(_.value), Some(Success(20)))
  }

  test("Task.chooseFirstOf(A,B) should not cancel A if B completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)
    var future = Option.empty[CancelableFuture[Int]]

    val t = Task.chooseFirstOf(ta, tb).map {
      case Left((a, futureB)) =>
        future = Some(futureB)
        a
      case Right((futureA, b)) =>
        future = Some(futureA)
        b
    }

    val f = t.runAsync
    s.tick(1.second)
    f.cancel()

    assertEquals(f.value, Some(Success(20)))
    assert(future.isDefined, "future.isDefined")
    assertEquals(future.flatMap(_.value), None)

    s.tick(1.second)
    assertEquals(future.flatMap(_.value), Some(Success(10)))
  }

  test("Task.chooseFirstOf(A,B) should end both in error if A completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.error[Int](dummy).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.chooseFirstOf(ta, tb)
    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.chooseFirstOf(A,B) should end both in error if B completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(2.seconds)
    val tb = Task.error[Int](dummy).delayExecution(1.second)

    val t = Task.chooseFirstOf(ta, tb)
    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.chooseFirstOf(A,B) should work if A completes second in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.error[Int](dummy).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t1 = Task.chooseFirstOf(ta, tb).flatMap {
      case Left((a, futureB)) =>
        Task.fromFuture(futureB).map(b => a + b)
      case Right((futureA, b)) =>
        Task.fromFuture(futureA).map(a => a + b)
    }

    val t2 = Task.chooseFirstOf(ta, tb).map {
      case Left((a, futureB)) => a
      case Right((futureA, b)) => b
    }

    val f1 = t1.runAsync
    val f2 = t2.runAsync
    s.tick(2.seconds)

    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Success(20)))
  }

  test("Task.chooseFirstOf(A,B) should work if B completes second in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(1.seconds)
    val tb = Task.error[Int](dummy).delayExecution(2.second)

    val t1 = Task.chooseFirstOf(ta, tb).flatMap {
      case Left((a, futureB)) =>
        Task.fromFuture(futureB).map(b => a + b)
      case Right((futureA, b)) =>
        Task.fromFuture(futureA).map(a => a + b)
    }

    val t2 = Task.chooseFirstOf(ta, tb).map {
      case Left((a, futureB)) => a
      case Right((futureA, b)) => b
    }

    val f1 = t1.runAsync
    val f2 = t2.runAsync
    s.tick(2.seconds)

    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Success(10)))
  }
}
