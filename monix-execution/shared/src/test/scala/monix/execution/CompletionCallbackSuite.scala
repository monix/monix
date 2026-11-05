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

package monix.execution

import java.util.concurrent.CancellationException
import minitest.TestSuite
import monix.execution.exceptions.{ CallbackCalledMultipleTimesException, DummyException }
import monix.execution.schedulers.TestScheduler
import scala.concurrent.Promise
import scala.util.{ Failure, Success, Try }

object CompletionCallbackSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  private final class RecordingCallback extends CompletionCallback[Int] {
    var outcome: Option[Either[Throwable, Int]] = None
    var cancelled = false

    override def onSuccess(value: Int): Unit = outcome = Some(Right(value))
    override def onError(error: Throwable): Unit = outcome = Some(Left(error))
    override def onCancellation(): Unit = cancelled = true
  }

  private def verifyAsyncWrapper(factory: CompletionCallback[Int] => CompletionCallback[Int])(implicit
    s: TestScheduler
  ): Unit = {
    val successTarget = new RecordingCallback
    val success = factory(successTarget)
    assert(success.tryOnSuccess(1))
    assert(!success.tryOnCancellation())
    s.tick()
    assertEquals(successTarget.outcome, Some(Right(1)))

    val errorTarget = new RecordingCallback
    val error = factory(errorTarget)
    val failure = DummyException("failure")
    assert(error.tryOnError(failure))
    assert(!error.tryOnSuccess(2))
    s.tick()
    assertEquals(errorTarget.outcome, Some(Left(failure)))

    val cancellationTarget = new RecordingCallback
    val cancellation = factory(cancellationTarget)
    assert(cancellation.tryOnCancellation())
    assert(!cancellation.tryOnSuccess(3))
    s.tick()
    assert(cancellationTarget.cancelled)
  }

  test("Either and Try failures, including CancellationException, are ordinary errors") { _ =>
    val callback = new RecordingCallback
    val cancellationError = new CancellationException("ordinary error")

    callback(Left(cancellationError))
    assertEquals(callback.outcome, Some(Left(cancellationError)))
    assert(!callback.cancelled)

    val fromTry = new RecordingCallback
    fromTry(Failure(cancellationError))
    assertEquals(fromTry.outcome, Some(Left(cancellationError)))
    assert(!fromTry.cancelled)
  }

  test("contramap forwards success, errors, and cancellation") { _ =>
    val underlying = new RecordingCallback
    val callback = underlying.contramap[String](_.toInt)

    callback.onSuccess("42")
    assertEquals(underlying.outcome, Some(Right(42)))

    val error = DummyException("failure")
    callback.onError(error)
    assertEquals(underlying.outcome, Some(Left(error)))

    callback.onCancellation()
    assert(underlying.cancelled)
  }

  test("fromPromise completes with success, error, and cancellation") { _ =>
    val successPromise = Promise[Int]()
    CompletionCallback.fromPromise(successPromise).onSuccess(1)
    assertEquals(successPromise.future.value, Some(Success(1)))

    val errorPromise = Promise[Int]()
    val error = DummyException("failure")
    CompletionCallback.fromPromise(errorPromise).onError(error)
    assertEquals(errorPromise.future.value, Some(Failure(error)))

    val cancellationPromise = Promise[Int]()
    CompletionCallback.fromPromise(cancellationPromise).onCancellation()
    val cancellation = cancellationPromise.future.value.collect { case Failure(e) => e }
    assert(cancellation.exists(_.isInstanceOf[CancellationException]))
  }

  test("fromPromise rejects a second terminal signal") { _ =>
    val success = CompletionCallback.fromPromise(Promise[Int]())
    success.onSuccess(1)
    assert(!success.tryOnError(DummyException("late")))
    val _ = intercept[CallbackCalledMultipleTimesException](success.onCancellation())

    val error = CompletionCallback.fromPromise(Promise[Int]())
    error.onError(DummyException("failure"))
    assert(!error.tryOnSuccess(2))
    val _ = intercept[CallbackCalledMultipleTimesException](error.onCancellation())

    val cancelled = CompletionCallback.fromPromise(Promise[Int]())
    cancelled.onCancellation()
    assert(!cancelled.tryOnSuccess(3))
    val _ = intercept[CallbackCalledMultipleTimesException](cancelled.onCancellation())
  }

  test("safe forwards all outcomes and rejects duplicates") { implicit s =>
    val successTarget = new RecordingCallback
    val success = CompletionCallback.safe(successTarget)
    success.onSuccess(1)
    assertEquals(successTarget.outcome, Some(Right(1)))
    assert(!success.tryOnCancellation())
    val _ = intercept[CallbackCalledMultipleTimesException](success.onSuccess(2))

    val errorTarget = new RecordingCallback
    val error = CompletionCallback.safe(errorTarget)
    val failure = DummyException("failure")
    error.onError(failure)
    assertEquals(errorTarget.outcome, Some(Left(failure)))
    assert(!error.tryOnSuccess(2))
    val _ = intercept[CallbackCalledMultipleTimesException](error.onError(failure))

    val cancellationTarget = new RecordingCallback
    val cancellation = CompletionCallback.safe(cancellationTarget)
    cancellation.onCancellation()
    assert(cancellationTarget.cancelled)
    assert(!cancellation.tryOnCancellation())
    val _ = intercept[CallbackCalledMultipleTimesException](cancellation.onCancellation())
  }

  test("forked callback forwards each outcome asynchronously and rejects duplicates") { implicit s =>
    verifyAsyncWrapper(callback => CompletionCallback.forked(callback))
  }

  test("trampolined callback forwards each outcome asynchronously") { implicit s =>
    verifyAsyncWrapper(callback => CompletionCallback.trampolined(callback))
  }

  test("function bridges forward success, errors, and explicit cancellation") { _ =>
    var attempt = Option.empty[Either[Throwable, Int]]
    val attemptSuccess = CompletionCallback.fromAttempt[Int](result => attempt = Some(result))
    attemptSuccess.onSuccess(1)
    assertEquals(attempt, Some(Right(1)))

    val attemptError = CompletionCallback.fromAttempt[Int](result => attempt = Some(result))
    val error = DummyException("failure")
    attemptError.onError(error)
    assertEquals(attempt, Some(Left(error)))

    val attemptCancellation = CompletionCallback.fromAttempt[Int](result => attempt = Some(result))
    attemptCancellation.onCancellation()
    assert(attempt.exists {
      case Left(_: CancellationException) => true
      case _ => false
    })

    var tryResult = Option.empty[Try[Int]]
    val trySuccess = CompletionCallback.fromTry[Int](result => tryResult = Some(result))
    trySuccess.onSuccess(1)
    assertEquals(tryResult, Some(Success(1)))

    val tryError = CompletionCallback.fromTry[Int](result => tryResult = Some(result))
    tryError.onError(error)
    assertEquals(tryResult, Some(Failure(error)))

    val tryCancellation = CompletionCallback.fromTry[Int](result => tryResult = Some(result))
    tryCancellation.onCancellation()
    assert(tryResult.exists {
      case Failure(_: CancellationException) => true
      case _ => false
    })

    val _ = intercept[CallbackCalledMultipleTimesException](trySuccess.onCancellation())
  }

  test("function adapters forward cancellation exceptions as ordinary errors") { _ =>
    val attemptTarget = new RecordingCallback
    attemptTarget.toAttempt(Right(1))
    assertEquals(attemptTarget.outcome, Some(Right(1)))

    val attemptErrorTarget = new RecordingCallback
    val cancellationError = new CancellationException("ordinary error")
    attemptErrorTarget.toAttempt(Left(cancellationError))
    assertEquals(attemptErrorTarget.outcome, Some(Left(cancellationError)))
    assert(!attemptErrorTarget.cancelled)

    val tryTarget = new RecordingCallback
    tryTarget.toTry(Success(1))
    assertEquals(tryTarget.outcome, Some(Right(1)))

    val tryErrorTarget = new RecordingCallback
    tryErrorTarget.toTry(Failure(cancellationError))
    assertEquals(tryErrorTarget.outcome, Some(Left(cancellationError)))
    assert(!tryErrorTarget.cancelled)
  }

  test("empty callback reports errors and ignores success and cancellation") { implicit s =>
    val empty = CompletionCallback.empty[Int]
    val error = DummyException("failure")

    empty.onSuccess(1)
    empty.onCancellation()
    empty.onError(error)

    assertEquals(s.state.lastReportedError, error)
  }
}
