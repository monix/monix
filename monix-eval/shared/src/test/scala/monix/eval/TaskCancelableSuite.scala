/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success, Try}

object TaskCancelableSuite extends BaseTestSuite {
  test("Task.cancelable0 should be stack safe on repeated, right-associated binds") { implicit s =>
    def signal[A](a: A): Task[A] = Task.cancelable0[A] { (_, cb) =>
      cb.onSuccess(a)
      Task.unit
    }

    val task = (0 until 10000).foldLeft(Task.now(0))((acc, _) => acc.flatMap(x => signal(x + 1)))
    val f = task.runToFuture
    s.tick()

    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.cancelable0 should be stack safe on repeated, left-associated binds") { implicit s =>
    def signal[A](a: A): Task[A] = Task.cancelable0[A] { (_, cb) =>
      cb.onSuccess(a)
      Task.unit
    }

    val task = (0 until 10000).foldLeft(Task.now(0))((acc, _) => signal(1).flatMap(x => acc.map(y => x + y)))
    val f = task.runToFuture
    s.tick()

    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.cancelable0 should work onSuccess") { implicit s =>
    val t = Task.cancelable0[Int] { (_, cb) =>
      cb.onSuccess(10); Task.unit
    }
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.cancelable0 should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.cancelable0[Int] { (_, cb) =>
      cb.onError(dummy); Task.unit
    }
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.cancelable0 should work when executed as future") { implicit s =>
    val t = Task.cancelable0[Int] { (_, cb) =>
      cb.onSuccess(100); Task.unit
    }
    val result = t.runToFuture

    s.tick()
    assertEquals(result.value, Some(Success(100)))
  }

  test("Task.cancelable0 should work when executed with callback") { implicit s =>
    var result = Option.empty[Try[Int]]
    val t = Task.cancelable0[Int] { (_, cb) =>
      cb.onSuccess(100); Task.unit
    }
    t.runAsync(Callback.fromTry[Int]({ r =>
      result = Some(r)
    }))

    s.tick()
    assertEquals(result, Some(Success(100)))
  }

  test("Task.cancelable works for immediate successful value") { implicit sc =>
    val task = Task.cancelable[Int] { cb =>
      cb.onSuccess(1); Task.unit
    }
    val f = task.runToFuture

    sc.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.cancelable works for immediate error") { implicit sc =>
    val e = DummyException("dummy")
    val task = Task.cancelable[Int] { cb =>
      cb.onError(e); Task.unit
    }
    val f = task.runToFuture

    sc.tick()
    assertEquals(f.value, Some(Failure(e)))
  }

  test("Task.cancelable is memory safe in flatMap loops") { implicit sc =>
    def signal(n: Int): Task[Int] =
      Task.cancelable { cb =>
        cb.onSuccess(n); Task.unit
      }

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { n =>
        if (n > 0) loop(n - 1, acc + 1)
        else Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.cancelable is cancelable") { implicit sc =>
    val c = BooleanCancelable()
    val f = Task.cancelable[Int](_ => Task(c.cancel())).runToFuture

    assertEquals(f.value, None)
    f.cancel()
    assertEquals(f.value, None)
    assert(c.isCanceled)
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.cancelable0(allowContinueOnCallingThread = true) should be stack safe on repeated, right-associated binds") {
    implicit s =>
      def signal[A](a: A): Task[A] =
        Task.cancelable0[A]({ (_, cb) =>
          cb.onSuccess(a)
          Task.unit
        }, allowContinueOnCallingThread = true)

      val task = (0 until 10000).foldLeft(Task.now(0))((acc, _) => acc.flatMap(x => signal(x + 1)))
      val f = task.runToFuture
      s.tick()

      assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.cancelable0(allowContinueOnCallingThread = true) should be stack safe on repeated, left-associated binds") {
    implicit s =>
      def signal[A](a: A): Task[A] =
        Task.cancelable0[A]({ (_, cb) =>
          cb.onSuccess(a)
          Task.unit
        }, allowContinueOnCallingThread = true)

      val task = (0 until 10000).foldLeft(Task.now(0))((acc, _) => signal(1).flatMap(x => acc.map(y => x + y)))
      val f = task.runToFuture
      s.tick()

      assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.cancelable0(allowContinueOnCallingThread = true) should work onSuccess") { implicit s =>
    val t = Task.cancelable0[Int]({ (_, cb) =>
      cb.onSuccess(10); Task.unit
    }, allowContinueOnCallingThread = true)
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.cancelable0(allowContinueOnCallingThread = true) should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.cancelable0[Int]({ (_, cb) =>
      cb.onError(dummy); Task.unit
    }, allowContinueOnCallingThread = true)
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.cancelable0(allowContinueOnCallingThread = true) should execute immediately when executed as future") {
    implicit s =>
      val t = Task.cancelable0[Int]({ (_, cb) =>
        cb.onSuccess(100); Task.unit
      }, allowContinueOnCallingThread = true)
      val result = t.runToFuture
      assertEquals(result.value, Some(Success(100)))
  }

  test("Task.cancelable0(allowContinueOnCallingThread = true) should execute immediately when executed with callback") {
    implicit s =>
      var result = Option.empty[Try[Int]]
      val t = Task.cancelable0[Int]({ (_, cb) =>
        cb.onSuccess(100); Task.unit
      }, allowContinueOnCallingThread = true)
      t.runAsync(Callback.fromTry[Int]({ r =>
        result = Some(r)
      }))
      assertEquals(result, Some(Success(100)))
  }

  test("Task.cancelable(allowContinueOnCallingThread = true) works for immediate successful value") { implicit sc =>
    val task = Task.cancelable[Int]({ cb =>
      cb.onSuccess(1); Task.unit
    }, allowContinueOnCallingThread = true)
    assertEquals(task.runToFuture.value, Some(Success(1)))
  }

  test("Task.cancelable(allowContinueOnCallingThread = true) works for immediate error") { implicit sc =>
    val e = DummyException("dummy")
    val task = Task.cancelable[Int](cb => { cb.onError(e); Task.unit }, allowContinueOnCallingThread = true)
    assertEquals(task.runToFuture.value, Some(Failure(e)))
  }

  test("Task.cancelable(allowContinueOnCallingThread = true) is memory safe in flatMap loops") { implicit sc =>
    def signal(n: Int): Task[Int] =
      Task.cancelable(cb => { cb.onSuccess(n); Task.unit }, allowContinueOnCallingThread = true)

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { n =>
        if (n > 0) loop(n - 1, acc + 1)
        else Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.cancelable(allowContinueOnCallingThread = true) is cancelable") { implicit sc =>
    val c = BooleanCancelable()
    val f = Task.cancelable[Int](_ => Task(c.cancel()), allowContinueOnCallingThread = true).runToFuture

    assertEquals(f.value, None)
    f.cancel()
    assertEquals(f.value, None)
    assert(c.isCanceled)
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }
}
