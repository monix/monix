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

import minitest.SimpleTestSuite
import monix.execution.{ CompletionCallback, Scheduler }
import monix.execution.schedulers.TestScheduler

import scala.util.{ Failure, Success }

object TaskBuildersSuite extends SimpleTestSuite {
  test("asyncF defers and evaluates effectful registration") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var registrations = 0

    val source = Task.fromAsyncF[Int] { callback =>
      Task.delay {
        registrations += 1
        callback.onSuccess(42)
      }
    }

    assertEquals(registrations, 0)
    val result = source.unsafeRunToFuture()
    assertEquals(registrations, 0)

    scheduler.tick()

    assertEquals(registrations, 1)
    assertEquals(result.value, Some(Success(42)))
  }

  test("asyncF resumes with delayed callback success and error") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var successCallback = Option.empty[CompletionCallback[Int]]
    var errorCallback = Option.empty[CompletionCallback[Int]]
    val expected = new RuntimeException("callback")

    val success = Task.fromAsyncF[Int] { callback =>
      Task.delay { successCallback = Some(callback) }
    }.unsafeRunToFuture()
    val failure = Task.fromAsyncF[Int] { callback =>
      Task.delay { errorCallback = Some(callback) }
    }.unsafeRunToFuture()

    scheduler.tick()
    assertEquals(success.value, None)
    assertEquals(failure.value, None)
    assert(successCallback.nonEmpty)
    assert(errorCallback.nonEmpty)

    successCallback.foreach(_.onSuccess(21))
    errorCallback.foreach(_.onError(expected))
    scheduler.tick()

    assertEquals(success.value, Some(Success(21)))
    assertEquals(failure.value, Some(Failure(expected)))
  }

  test("asyncF callback wait remains cancelable after registration completes") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var finalized = false

    val result = Task.fromAsyncF[Int](_ => Task.unit)
      .onCancel(Task.delay { finalized = true })
      .unsafeRunToFuture()

    scheduler.tick()
    assertEquals(result.value, None)
    assert(!finalized)

    result.cancel()
    scheduler.tick()

    assert(finalized)
  }

  test("cancelable reports synchronous success and error") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val expected = new RuntimeException("callback")

    val success = Task.fromCancelable[Int] { callback =>
      callback.onSuccess(42)
      Task.unit
    }.unsafeRunToFuture()
    val failure = Task.fromCancelable[Int] { callback =>
      callback.onError(expected)
      Task.unit
    }.unsafeRunToFuture()

    scheduler.tick()

    assertEquals(success.value, Some(Success(42)))
    assertEquals(failure.value, Some(Failure(expected)))
  }

  test("cancelable evaluates its token only on cancellation and waits for it") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var registrations = 0
    var tokenRuns = 0
    var finishToken = Option.empty[CompletionCallback[Unit]]

    val cancel = Task.fromCancelable[Int] { _ =>
      registrations += 1
      Task.fromAsync[Unit] { callback =>
        tokenRuns += 1
        finishToken = Some(callback)
      }
    }.unsafeRunAsync(CompletionCallback.empty)

    assertEquals(registrations, 0)
    scheduler.tick()
    assertEquals(registrations, 1)
    assertEquals(tokenRuns, 0)

    val canceled = cancel.unsafeRunToFuture()
    scheduler.tick()

    assertEquals(tokenRuns, 1)
    assertEquals(canceled.value, None)

    finishToken.foreach(_.onSuccess(()))
    scheduler.tick()

    assertEquals(canceled.value, Some(Success(())))
  }

  test("registration exceptions become task errors") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val expected = new RuntimeException("registration")

    val asyncF = Task.fromAsyncF[Int](_ => throw expected).unsafeRunToFuture()
    val cancelable = Task.fromCancelable[Int](_ => throw expected).unsafeRunToFuture()
    val cancelable0 = Task.fromCancelable0[Int]((_, _) => throw expected).unsafeRunToFuture()

    scheduler.tick()

    assertEquals(asyncF.value, Some(Failure(expected)))
    assertEquals(cancelable.value, Some(Failure(expected)))
    assertEquals(cancelable0.value, Some(Failure(expected)))
    assertEquals(scheduler.state.lastReportedError, null)
  }

  test("cancelable0 receives the current scheduler") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var observedScheduler = Option.empty[Scheduler]

    val result = Task.fromCancelable0[Int] { (current, callback) =>
      observedScheduler = Some(current)
      callback.onSuccess(42)
      Task.unit
    }.unsafeRunToFuture()

    scheduler.tick()

    assertEquals(observedScheduler, Some(scheduler))
    assertEquals(result.value, Some(Success(42)))
  }
}
