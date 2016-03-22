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

package monix.async

import minitest.TestSuite
import monix.execution.Cancelable
import monix.execution.cancelables.BooleanCancelable
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}
import scala.util.{Try, Failure, Success}

object TaskTest extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("Task.apply should work, on different thread") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.apply should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task.eval should execute on same thread") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task.evalAlways(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.fork should execute asynchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task.fork(Task.evalAlways(trigger()))
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.create should work for onSuccess") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = {
      val ref = Task.create[String] { (cb, s) => cb.onSuccess(trigger()); Cancelable.empty }
      Task.fork(ref)
    }

    assert(!wasTriggered, "!wasTriggered")
    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.create should work for onError") { implicit s =>
    val ex = DummyException("dummy")
    val task = {
      val ref = Task.create[String] { (cb, s) => cb.onError(ex); Cancelable.empty }
      Task.fork(ref)
    }

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.create should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.create[Int] { (cb,s) => throw ex }.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task.fromFuture should onSuccess") { implicit s =>
    val f = Task.fromFuture(Future { 1 }).runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.fromFuture should onError") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.fromFuture(Future { throw ex }).runAsync
    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.now should work") { implicit s =>
    var received = 0
    Task.now(1).runAsync(_.foreach(x => received = x))
    s.tick()
    assertEquals(received, 1)
  }

  test("Task.now should already be completed on returned future") { implicit s =>
    val f = Task.now(1).runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.fail should work") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    Task.error(ex).runAsync(_.failed.foreach(x => received = x))
    s.tick()
    assertEquals(received, ex)
  }

  test("Task.fail should already be completed on returned future") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.error(ex).runAsync
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#runAsync should work") { implicit s =>
    val task = Task(1)
    var received = 0

    task.runAsync.onSuccess {
      case r => received = r
    }

    s.tick()
    assertEquals(received, 1)
  }

  test("Task#runAsync should signal error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex)
    var received: Throwable = null

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#runAsync(Callback) should protect against broken onSuccess and respect the contract") { implicit s =>
    val ex = DummyException("dummy")
    var receivedCount = 0
    val task = Task(1)

    task.runAsync(new Callback[Int] {
      def onSuccess(value: Int): Unit = {
        receivedCount += 1
        throw ex
      }
      def onError(ex: Throwable): Unit = {
        receivedCount += 1
      }
    })

    s.tick()
    assertEquals(receivedCount, 1)
    assertEquals(s.state.get.lastReportedError, ex)
  }

  test("Task#runAsync(Try[T] => Unit) should work") { implicit s =>
    val task = Task(1)
    var received = 0

    task.runAsync(_.foreach(x => received = x))

    s.tick()
    assertEquals(received, 1)
  }

  test("Task#runAsync(Try[T] => Unit) should signal error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex)
    var received: Throwable = null

    task.runAsync(_.failed.foreach(x => received = x))

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }


  test("Task#map should work onSuccess") { implicit s =>
    val task = Task(1 + 1).map(_ * 2)
    val f = task.runAsync

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(4)))
  }

  test("Task#map should work onError") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task[Int](throw ex).map(_ * 2)

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#map should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task(1).map(x => throw ex)

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#map is cancelable") { implicit s =>
    var wasTriggered = false
    val source = Task.create[String] { (callback, s) =>
      val cancelable = BooleanCancelable()
      s.execute(new Runnable {
        override def run(): Unit = {
          if (!cancelable.isCanceled) {
            wasTriggered = true
            callback.onSuccess("result")
          }
        }
      })

      cancelable
    }

    val task = source.map(x => x)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel()

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)
  }

  test("Task#flatten should work") { implicit s =>
    // note, this can trigger stack overflows
    def sum(n: Int): Task[Int] = {
      if (n == 0) Task.now(0) else
        Task(n).map(x => sum(x-1).map(_ + x)).flatten
    }

    val task = sum(100)
    val f = task.runAsync

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(5050)))
  }

  test("Task#flatMap should work") { implicit s =>
    // note, this can trigger stack overflows
    def sum(n: Int): Task[Int] = {
      if (n == 0) Task.now(0) else
        Task(n).flatMap(x => sum(x-1).map(_ + x))
    }

    val task = sum(100)
    val f = task.runAsync

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(5050)))
  }

  test("Task#flatMap should work onError") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task[Int](throw ex)
      .flatMap(x => Task.now(x * 2))

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task(1).flatMap(x => throw ex)

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#flatMap is cancelable") { implicit s =>
    var wasTriggered = false
    def sum(n: Int, acc: Long = 0): Task[Long] = {
      if (n == 0) Task { wasTriggered = true; acc } else
        Task(n).flatMap(x => sum(x-1, acc + x))
    }


    val task = sum(Platform.recommendedBatchSize * 2)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel()

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)
  }

  test("Task#delayExecution should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayExecution(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#delayExecution is cancelable") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayExecution(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assert(!wasTriggered, "!wasTriggered")

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty,
      "should cancel the scheduleOnce(delay) as well")
  }

  test("Task#delayResult should work") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayResult(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#delayResult is cancelable") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger()).delayResult(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty,
      "should cancel the scheduleOnce(delay) as well")
  }

  test("Task#failed should project the failure") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).failed.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#failed should fail if source is successful") { implicit s =>
    val f = Task(1).failed.runAsync

    s.tick()
    assert(f.value.get.isFailure && f.value.get.failed.get.isInstanceOf[NoSuchElementException],
      "isInstanceOf[NoSuchElementException]")
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#failed is not cancelable") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task(throw dummy).failed

    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()

    assertEquals(f.value, Some(Success(dummy)))
  }

  test("Task#onErrorRecover should mirror source on success") { implicit s =>
    val task = Task(1).onErrorHandle { case ex: Throwable => 99 }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorRecover should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](throw ex).onErrorHandle {
      case ex: DummyException => 99
    }

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#onErrorRecover should protect against user code") { implicit s =>
    val ex1 = DummyException("one")
    val ex2 = DummyException("two")

    val task = Task[Int](throw ex1)
      .onErrorHandle { case ex => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
    assertEquals(s.state.get.lastReportedError, ex1)
  }

  test("Task#onErrorRecover is not cancelable") { implicit s =>
    val task = Task[Int](throw DummyException("dummy"))
      .onErrorHandle { case _: DummyException => 99 }

    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    f.cancel(); s.tick()

    assertEquals(f.value, Some(Success(99)))
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
    val task = Task(throw ex).onErrorFallbackTo(throw err)
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Failure(err)))
  }

  test("Task.onErrorFallbackTo should be cancelable") { implicit s =>
    def recursive(): Task[Int] = {
      Task[Int](throw DummyException("dummy")).onErrorFallbackTo(recursive())
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
      .onErrorRetry(s.batchedExecutionModulus+2)

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
    val task = Task(1).onErrorHandleWith { case ex: Throwable => Task(99) }
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task#onErrorRecoverWith should recover") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task[Int](throw ex).onErrorHandleWith {
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
      .onErrorHandleWith { case ex => throw ex2 }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(ex2)))
    assertEquals(s.state.get.lastReportedError, ex1)
  }

  test("Task#onErrorRecoverWith is cancelable") { implicit s =>
    def recursive(): Task[Int] = {
      Task[Int](throw DummyException("dummy"))
        .onErrorHandleWith { case _: DummyException => recursive() }
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
      .onErrorHandleWith { case _: DummyException => Task(99).delayExecution(1.second) }

    val f = task.runAsync
    assertEquals(f.value, None)
    // cancelling after scheduled for execution, but before execution
    s.tick(); assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
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

  test("Task#ambWith should switch to other") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).ambWith(Task(99).delayExecution(1.second))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#ambWith should onError from other") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(1).delayExecution(10.seconds).ambWith(Task(throw ex).delayExecution(1.second))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#ambWith should mirror the source") { implicit s =>
    val task = Task(1).delayExecution(1.seconds).ambWith(Task(99).delayExecution(10.second))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task#ambWith should onError from the source") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex).delayExecution(1.seconds).ambWith(Task(99).delayExecution(10.second))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task#ambWith should cancel both") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).ambWith(Task(99).delayExecution(1.second))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "both should be canceled")
  }

  test("Task#zip should work if source finishes first") { implicit s =>
    val f = Task(1).zip(Task(2).delayExecution(1.second)).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1,2))))
  }

  test("Task#zip should work if other finishes first") { implicit s =>
    val f = Task(1).delayExecution(1.second).zip(Task(2)).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1,2))))
  }

  test("Task#zip should cancel both") { implicit s =>
    val f = Task(1).delayExecution(1.second).zip(Task(2).delayExecution(2.seconds)).runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zip should cancel just the source") { implicit s =>
    val f = Task(1).delayExecution(1.second).zip(Task(2).delayExecution(2.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zip should cancel just the other") { implicit s =>
    val f = Task(1).delayExecution(2.second).zip(Task(2).delayExecution(1.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zip should onError from the source before other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).delayExecution(1.second).zip(Task(2).delayExecution(2.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip should onError from the source after other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).delayExecution(2.second).zip(Task(2).delayExecution(1.seconds)).runAsync

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip should onError from the other after the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).delayExecution(1.second).zip(Task(throw ex).delayExecution(2.seconds)).runAsync

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip should onError from the other before the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).delayExecution(2.second).zip(Task(throw ex).delayExecution(1.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip illegal state exception from source") { implicit s =>
    val source = Task.unsafeCreate[Int] { (scheduler, cancelable, depth, cb) =>
      cb.onSuccess(1)
      cb.onSuccess(2)
    }

    val task = source.zip(Task(3).delayExecution(1.second))
    val f = task.runAsync
    s.tick()

    val ex = f.value.flatMap(_.failed.toOption).orNull
    assert(ex != null && ex.isInstanceOf[IllegalStateException],
      "should have thrown IllegalStateException")
  }

  test("Task#zip illegal state exception from other") { implicit s =>
    val other = Task.unsafeCreate[Int] { (scheduler, cancelable, depth, cb) =>
      cb.onSuccess(1)
      cb.onSuccess(2)
    }

    val task = Task(3).delayExecution(1.second).zip(other)
    val f = task.runAsync
    s.tick()

    val ex = f.value.flatMap(_.failed.toOption).orNull
    assert(ex != null && ex.isInstanceOf[IllegalStateException],
      "should have thrown IllegalStateException")
  }

  test("Task.firstCompletedOf should switch to other") { implicit s =>
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task.firstCompletedOf should onError from other") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(10.seconds), Task(throw ex).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.firstCompletedOf should mirror the source") { implicit s =>
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task.firstCompletedOf should onError from the source") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.firstCompletedOf(Seq(Task(throw ex).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task.firstCompletedOf should cancel both") { implicit s =>
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "both should be canceled")
  }

  test("Task.sequence should execute in parallel") { implicit s =>
    val seq = Seq(Task(1).delayExecution(2.seconds), Task(2).delayExecution(1.second), Task(3).delayExecution(3.seconds))
    val f = Task.sequence(seq).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(Seq(1, 2, 3))))
  }

  test("Task.sequence should onError if one of the tasks terminates in error") { implicit s =>
    val ex = DummyException("dummy")
    val seq = Seq(
      Task(3).delayExecution(3.seconds),
      Task(2).delayExecution(1.second),
      Task(throw ex).delayExecution(2.seconds),
      Task(3).delayExecution(1.seconds))

    val f = Task.sequence(seq).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)
    s.tick(1.seconds)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.sequence should be canceled") { implicit s =>
    val seq = Seq(Task(1).delayExecution(2.seconds), Task(2).delayExecution(1.second), Task(3).delayExecution(3.seconds))
    val f = Task.sequence(seq).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)

    f.cancel()
    s.tick(1.second)
    assertEquals(f.value, None)
  }


  test("Task.memoize should work synchronously for first subscriber") { implicit s =>
    val task = Task.evalAlways(1).memoize
    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.memoize should work synchronously for subsequent subscribers after complete") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.memoize
    task.runAsync

    val f1 = task.runAsync
    assertEquals(f1.value, Some(Success(1)))
    assertEquals(effect, 1)

    val f2 = task.runAsync
    assertEquals(f2.value, Some(Success(1)))
    assertEquals(effect, 1)
  }

  test("Task.memoize should queue subscribers while running") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.delayExecution(1.second).memoize
    task.runAsync

    val f1 = task.runAsync
    assertEquals(f1.value, None)
    assertEquals(effect, 0)

    val f2 = task.runAsync
    assertEquals(f2.value, None)
    assertEquals(effect, 0)

    s.tick(1.second)
    assertEquals(f1.value, Some(Success(1)))
    assertEquals(f2.value, Some(Success(1)))
    assertEquals(effect, 1)
  }

  test("Task.memoize should be cancelable, test 1") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.delayExecution(1.second).memoize
    val f = task.runAsync

    val f1 = task.runAsync
    assertEquals(f1.value, None)
    assertEquals(effect, 0)

    f1.cancel()
    s.tick()

    assertEquals(f.value, None)
    assertEquals(f1.value, None)
  }

  test("Task.memoize should be cancelable, test 2") { implicit s =>
    var effect = 0
    val task = Task.evalAlways { effect += 1; effect }.delayExecution(1.second).memoize
    val f = task.runAsync

    var f1 = Option.empty[Try[Int]]
    val c1 = task.runAsync{ result => f1 = Some(result) }
    assertEquals(f1, None)
    assertEquals(effect, 0)

    c1.cancel()
    s.tick()

    assertEquals(f.value, None)
    assertEquals(f1, None)
  }

  case class DummyException(message: String) extends RuntimeException(message) {
    def this() = this(null)
  }
}
