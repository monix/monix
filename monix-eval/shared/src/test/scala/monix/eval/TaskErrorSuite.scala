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
import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskErrorSuite extends BaseTestSuite {
  test("Task.attempt should expose error") { implicit s =>
    val dummy = DummyException("dummy")
    val r = Task.raiseError[Int](dummy).attempt.coeval.runTry
    assertEquals(r, Success(Right(Left(dummy))))
  }

  test("Task.attempt should work for successful values") { implicit s =>
    val r = Task.now(10).attempt.coeval.runTry
    assertEquals(r, Success(Right(Right(10))))
  }

  test("Task.fail should expose error") { implicit s =>
    val dummy = DummyException("dummy")
    val r = Task.raiseError[Int](dummy).failed.coeval.value
    assertEquals(r, Right(dummy))
  }

  test("Task.fail should fail for successful values") { implicit s =>
    intercept[NoSuchElementException] {
      Task.now(10).failed.coeval.value
    }
  }

  test("Task.now.materialize") { implicit s =>
    assertEquals(Task.now(10).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.error.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Task.raiseError[Int](dummy).materialize.runAsync.value, Some(Success(Failure(dummy))))
  }

  test("Task.evalOnce.materialize") { implicit s =>
    assertEquals(Task.evalOnce(10).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.evalOnce.materialize should be stack safe") { implicit s =>
    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.evalOnce(n)
      else Task.evalOnce(n).materialize.flatMap {
        case Success(_) => loop(n-1)
        case Failure(ex) => Task.raiseError(ex)
      }

    val count = if (Platform.isJVM) 50000 else 5000
    val result = loop(count).runAsync; s.tick()
    assertEquals(result.value, Some(Success(0)))
  }

  test("Task.eval.materialize") { implicit s =>
    assertEquals(Task.eval(10).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.defer.materialize") { implicit s =>
    assertEquals(Task.defer(Task.now(10)).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.defer.flatMap.materialize") { implicit s =>
    assertEquals(Task.defer(Task.now(10)).flatMap(Task.now).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.error.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Task.raiseError[Int](dummy).materialize.coeval.value, Right(Failure(dummy)))
  }

  test("Task.flatMap.materialize") { implicit s =>
    assertEquals(Task.eval(10).flatMap(x => Task.now(x))
      .materialize.coeval.value, Right(Success(10)))
  }

  test("Task.apply.materialize") { implicit s =>
    val f = Task(10).materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Success(10))))
  }

  test("Task.apply.flatMap.materialize") { implicit s =>
    val f = Task(10).flatMap(Task.now).materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Success(10))))
  }

  test("Task.apply(error).flatMap.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task[Int](throw dummy).flatMap(Task.now).materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.now.flatMap(error).materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.now(10).flatMap[Int](_ => throw dummy).materialize.runAsync
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.defer(error).materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.defer[Int](throw dummy).materialize.runAsync
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.defer(error).flatMap.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.defer[Int](throw dummy).flatMap(Task.now).materialize.runAsync
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.now.dematerialize") { implicit s =>
    val result = Task.now(10).materialize.dematerialize.runAsync.value
    assertEquals(result, Some(Success(10)))
  }

  test("Task.error.dematerialize") { implicit s =>
    val dummy = DummyException("dummy")
    val result = Task.raiseError[Int](dummy).materialize.dematerialize.runAsync.value
    assertEquals(result, Some(Failure(dummy)))
  }

  test("Task#onErrorRecover should mirror source on success") { implicit s =>
    val task = Task(1).onErrorRecover { case _: Throwable => 99 }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorRecover should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](if (1 == 1) throw ex else 1).onErrorRecover {
      case _: DummyException => 99
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorRecover should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](if (1 == 1) throw ex1 else 1)
      .onErrorRecover { case _ => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
  }

  test("Task#onErrorHandle should mirror source on success") { implicit s =>
    val task = Task(1).onErrorHandle { _: Throwable => 99 }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorHandle should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](if (1 == 1) throw ex else 1).onErrorHandle {
      case _: DummyException => 99
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorHandle should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](if (1 == 1) throw ex1 else 1)
      .onErrorHandle { _ => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
  }

  test("Task.onErrorFallbackTo should mirror source onSuccess") { implicit s =>
    val task = Task(1).onErrorFallbackTo(Task(2))
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.onErrorFallbackTo should fallback to backup onError") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex).onErrorFallbackTo(Task(2))
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("Task.onErrorFallbackTo should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val err = DummyException("unexpected")
    val task = Task(throw ex).onErrorFallbackTo(Task.defer(throw err))
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Failure(err)))
  }

  test("Task.onErrorFallbackTo should be cancelable if the ExecutionModel allows it") { implicit s =>
    def recursive(): Task[Int] = {
      Task[Int](throw DummyException("dummy")).onErrorFallbackTo(Task.defer(recursive()))
    }

    val task = recursive().executeWithOptions(_.enableAutoCancelableRunLoops)
    val f = task.runAsync
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task.onErrorRestart should mirror the source onSuccess") { implicit s =>
    var tries = 0
    val task = Task.eval { tries += 1; 1 }.onErrorRestart(10)
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 1)
  }

  test("Task.onErrorRestart should retry onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.eval { tries += 1; if (tries < 5) throw ex else 1 }.onErrorRestart(10)
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 5)
  }

  test("Task.onErrorRestart should emit onError after max retries") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.eval { tries += 1; throw ex }.onErrorRestart(10)
    val f = task.runAsync

    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(tries, 11)
  }

  test("Task.onErrorRestart should be cancelable if ExecutionModel permits") { implicit s =>
    val task = Task[Int](throw DummyException("dummy"))
      .onErrorRestart(s.executionModel.recommendedBatchSize*2)

    val f = task.executeWithOptions(_.enableAutoCancelableRunLoops).runAsync
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task.onErrorRestartIf should mirror the source onSuccess") { implicit s =>
    var tries = 0
    val task = Task.eval { tries += 1; 1 }.onErrorRestartIf(ex => tries < 10)
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 1)
  }

  test("Task.onErrorRestartIf should retry onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.eval { tries += 1; if (tries < 5) throw ex else 1 }
      .onErrorRestartIf(_ => tries <= 10)

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 5)
  }

  test("Task.onErrorRestartIf should emit onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.eval { tries += 1; throw ex }
      .onErrorRestartIf(_ => tries <= 10)

    val f = task.runAsync
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(tries, 11)
  }

  test("Task.onErrorRestartIf should be cancelable if ExecutionModel permits") { implicit s =>
    val task = Task[Int](throw DummyException("dummy")).onErrorRestartIf(_ => true)
    val f = task.executeWithOptions(_.enableAutoCancelableRunLoops).runAsync
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task#onErrorRecoverWith should mirror source on success") { implicit s =>
    val task = Task(1).onErrorRecoverWith { case _: Throwable => Task(99) }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorRecoverWith should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](throw ex).onErrorRecoverWith {
      case _: DummyException => Task(99)
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorRecoverWith should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](throw ex1)
      .onErrorRecoverWith { case _ => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
  }

  test("Task#onErrorRecoverWith has a cancelable fallback") { implicit s =>
    val task = Task[Int](throw DummyException("dummy"))
      .onErrorRecoverWith { case _: DummyException => Task(99).delayExecution(1.second) }

    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    s.tick(); assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task#onErrorRecover should emit error if not matches") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task[Int](throw dummy).onErrorRecover { case _: TimeoutException => 10 }
    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task#onErrorRecoverWith should emit error if not matches") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task[Int](throw dummy).onErrorRecoverWith { case _: TimeoutException => Task.now(10) }
    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.eval.flatMap.onErrorRecoverWith should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.eval(1).flatMap(_ => throw dummy)
      .onErrorRecoverWith { case `dummy` => Task.now(10) }

    val f = task.runAsync
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.apply.flatMap.onErrorRecoverWith should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task(1).flatMap(_ => throw dummy)
      .onErrorRecoverWith { case `dummy` => Task.now(10) }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.apply.materialize should work for success") { implicit s =>
    val task = Task.apply(1).materialize
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Success(1))))
  }

  test("Task.apply.materialize should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int] { throw dummy }.materialize
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.apply.materialize should be stack safe") { implicit s =>
    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.apply(n)
      else Task.apply(n).materialize.flatMap {
        case Success(_) => loop(n-1)
        case Failure(e) => Task.raiseError(e)
      }

    val count = if (Platform.isJVM) 50000 else 5000
    val result = loop(count).runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0)))
  }

  test("Task.eval.materialize should work for success") { implicit s =>
    val task = Task.eval(1).materialize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Success(1))))
  }

  test("Task.eval.materialize should work for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.eval[Int](throw dummy).materialize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.eval.materialize should be stack safe") { implicit s =>
    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.eval(n)
      else Task.eval(n).materialize.flatMap {
        case Success(_) => loop(n-1)
        case Failure(e) => Task.raiseError(e)
      }

    val count = if (Platform.isJVM) 50000 else 5000
    val result = loop(count).runAsync; s.tick()
    assertEquals(result.value, Some(Success(0)))
  }

  test("Task.now.materialize should work") { implicit s =>
    val task = Task.now(1).materialize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Success(1))))
  }

  test("Task.error.materialize should work") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy).materialize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(Failure(dummy))))
  }

  test("Task.materialize on failing flatMap") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.now(1).flatMap { _ => (throw ex) : Task[Int] }
    val materialized = task.materialize.runAsync
    assertEquals(materialized.value, Some(Success(Failure(ex))))
  }

  test("Task.now.materialize should be stack safe") { implicit s =>
    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.now(n)
      else Task.now(n).materialize.flatMap {
        case Success(v) => loop(v-1)
        case Failure(ex) => Task.raiseError(ex)
      }

    val count = if (Platform.isJVM) 50000 else 5000
    val result = loop(count).runAsync; s.tick()
    assertEquals(result.value, Some(Success(0)))
  }

  test("Task.raiseError.dematerialize") { implicit s =>
    val ex = DummyException("dummy")
    val result = Task.raiseError[Int](ex).materialize.dematerialize.runAsync
    assertEquals(result.value, Some(Failure(ex)))
  }

  test("Task.now.onErrorRecoverWith should be stack safe") { implicit s =>
    val ex = DummyException("dummy1")
    val count = if (Platform.isJVM) 50000 else 5000

    def loop(task: Task[Int], n: Int): Task[Int] =
      task.onErrorRecoverWith {
        case `ex` if n <= 0 => Task.now(count)
        case `ex` => loop(task, n-1)
      }

    val task = loop(Task.raiseError(ex), count)
    val result = task.runAsync

    s.tick()
    assertEquals(result.value, Some(Success(count)))
  }

  test("Task.async.onErrorRecoverWith should be stack safe") { implicit s =>
    val ex = DummyException("dummy1")
    val count = if (Platform.isJVM) 50000 else 5000

    def loop(task: Task[Int], n: Int): Task[Int] =
      task.onErrorRecoverWith {
        case `ex` if n <= 0 => Task(count)
        case `ex` => Task.fork(loop(task, n-1))
      }

    val task = loop(Task.raiseError(ex), count)
    val result = task.runAsync

    s.tick()
    assertEquals(result.value, Some(Success(count)))
  }
}
