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

import scala.util.{Failure, Success}
import concurrent.duration._
import scala.concurrent.TimeoutException

object TaskErrorSuite extends BaseTestSuite {
  test("Task.failed should expose error") { implicit s =>
    val dummy = DummyException("ex")
    val r = Task.error[Int](dummy).failed.coeval.runTry
    assertEquals(r, Success(Right(dummy)))
  }

  test("Task.failed should end in error on success") { implicit s =>
    intercept[NoSuchElementException] {
      Task.now(10).failed.coeval.value
    }
  }

  test("Task.now.materialize") { implicit s =>
    assertEquals(Task.now(10).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.error.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Task.error[Int](dummy).materialize.runAsync.value, Some(Success(Failure(dummy))))
  }

  test("Task.evalOnce.materialize") { implicit s =>
    assertEquals(Task.evalOnce(10).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.evalAlways.materialize") { implicit s =>
    assertEquals(Task.evalAlways(10).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.defer.materialize") { implicit s =>
    assertEquals(Task.defer(Task.now(10)).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.defer.flatMap.materialize") { implicit s =>
    assertEquals(Task.defer(Task.now(10)).flatMap(Task.now).materialize.coeval.value, Right(Success(10)))
  }

  test("Task.error.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    assertEquals(Task.error[Int](dummy).materialize.coeval.value, Right(Failure(dummy)))
  }

  test("Task.flatMap.materialize") { implicit s =>
    assertEquals(Task.evalAlways(10).flatMap(x => Task.now(x))
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

  test("Task.apply.memoize.materialize") { implicit s =>
    val f = Task(10).memoize.materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Success(10))))
  }

  test("Task.apply(error).memoize.materialize") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task[Int](throw dummy).memoize.materialize.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(Failure(dummy))))
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
    val result = Task.error[Int](dummy).materialize.dematerialize.runAsync.value
    assertEquals(result, Some(Failure(dummy)))
  }

  test("Task.now.dematerializeAttempt") { implicit s =>
    val result = Task.now(10).materializeAttempt.dematerializeAttempt.runAsync.value
    assertEquals(result, Some(Success(10)))
  }

  test("Task.error.dematerializeAttempt") { implicit s =>
    val dummy = DummyException("dummy")
    val result = Task.error[Int](dummy).materializeAttempt.dematerializeAttempt.runAsync.value
    assertEquals(result, Some(Failure(dummy)))
  }

  test("Task#onErrorRecover should mirror source on success") { implicit s =>
    val task = Task(1).onErrorRecover { case ex: Throwable => 99 }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorRecover should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](if (1 == 1) throw ex else 1).onErrorRecover {
      case ex: DummyException => 99
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorRecover should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](if (1 == 1) throw ex1 else 1)
      .onErrorRecover { case ex => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
  }

  test("Task#onErrorRecover is cancelable") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fork(Task.error[Int](dummy))
      .onErrorRecover { case _: DummyException => 99 }

    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task#onErrorHandle should mirror source on success") { implicit s =>
    val task = Task(1).onErrorHandle { case ex: Throwable => 99 }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorHandle should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](if (1 == 1) throw ex else 1).onErrorHandle {
      case ex: DummyException => 99
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorHandle should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](if (1 == 1) throw ex1 else 1)
      .onErrorHandle { case ex => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
  }

  test("Task#onErrorHandle is cancelable") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fork(Task.error[Int](dummy))
      .onErrorHandle { case _: DummyException => 99 }

    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
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

  test("Task.onErrorFallbackTo should be cancelable") { implicit s =>
    def recursive(): Task[Int] = {
      Task[Int](throw DummyException("dummy")).onErrorFallbackTo(Task.defer(recursive()))
    }

    val task = recursive()
    val f = task.runAsync
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task.onErrorRetry should mirror the source onSuccess") { implicit s =>
    var tries = 0
    val task = Task.evalAlways { tries += 1; 1 }.onErrorRetry(10)
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 1)
  }

  test("Task.onErrorRetry should retry onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.evalAlways { tries += 1; if (tries < 5) throw ex else 1 }.onErrorRetry(10)
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 5)
  }

  test("Task.onErrorRetry should emit onError after max retries") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.evalAlways { tries += 1; throw ex }.onErrorRetry(10)
    val f = task.runAsync

    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(tries, 11)
  }

  test("Task.onErrorRetry should not be cancelable") { implicit s =>
    val task = Task[Int](throw DummyException("dummy"))
      .onErrorRetry(s.executionModel.recommendedBatchSize*2)

    val f = task.runAsync
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
  }

  test("Task.onErrorRetryIf should mirror the source onSuccess") { implicit s =>
    var tries = 0
    val task = Task.evalAlways { tries += 1; 1 }.onErrorRetryIf(ex => tries < 10)
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 1)
  }

  test("Task.onErrorRetryIf should retry onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.evalAlways { tries += 1; if (tries < 5) throw ex else 1 }
      .onErrorRetryIf(ex => tries <= 10)

    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
    assertEquals(tries, 5)
  }

  test("Task.onErrorRetryIf should emit onError") { implicit s =>
    val ex = DummyException("dummy")
    var tries = 0
    val task = Task.evalAlways { tries += 1; throw ex }
      .onErrorRetryIf(ex => tries <= 10)

    val f = task.runAsync
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(tries, 11)
  }

  test("Task.onErrorRetryIf should be cancelable") { implicit s =>
    val task = Task[Int](throw DummyException("dummy")).onErrorRetryIf(ex => true)
    val f = task.runAsync
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()

    assertEquals(f.value, None)
  }

  test("Task#onErrorRecoverWith should mirror source on success") { implicit s =>
    val task = Task(1).onErrorRecoverWith { case ex: Throwable => Task(99) }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorRecoverWith should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](throw ex).onErrorRecoverWith {
      case ex: DummyException => Task(99)
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorRecoverWith should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](throw ex1)
      .onErrorRecoverWith { case ex => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
  }

  test("Task#onErrorRecoverWith is cancelable") { implicit s =>
    def recursive(): Task[Int] = {
      Task[Int](throw DummyException("dummy"))
        .onErrorRecoverWith { case _: DummyException => recursive() }
    }

    val task = recursive()
    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()
    assertEquals(f.value, None)
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
}
