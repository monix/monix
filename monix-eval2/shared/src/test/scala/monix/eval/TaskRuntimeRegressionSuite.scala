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

import cats.effect.kernel.{ Async, Outcome }
import minitest.SimpleTestSuite
import monix.execution.{ Callback, ExecutionModel }
import monix.execution.schedulers.TestScheduler
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

object TaskRuntimeRegressionSuite extends SimpleTestSuite {
  test("cancellation finalizer retains ownership after a success callback") {
    assertCancellationFinalizerRetainsOwnership(_.onSuccess(()))
  }

  test("cancellation finalizer retains ownership after an error callback") {
    val error = new RuntimeException("async failure")
    assertCancellationFinalizerRetainsOwnership(_.onError(error))
  }

  test("evalOn publishes cancellation through join") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val result = Async[Task]
      .evalOn(Task.canceled, target)
      .start
      .flatMap(_.join)
      .unsafeRunToFuture()

    tick(source, target)

    assert(result.value.exists {
      case Success(outcome) => outcome.isCanceled
      case _ => false
    })
  }

  test("evalOn preserves cancellation through a polled region") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val result = Task
      .uncancelable { poll =>
        Async[Task].evalOn(poll(Task.canceled), target)
      }
      .start
      .flatMap(_.join)
      .unsafeRunToFuture()

    tick(source, target)

    assert(result.value.exists {
      case Success(outcome) => outcome.isCanceled
      case _ => false
    })
  }

  test("racePair cancellation requests both children before waiting for finalizers") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var childAStarted = false
    var childBStarted = false
    var childAFinalizerWaiting = false
    var childAFinalizerFinished = false
    var childBFinalizerFinished = false

    val F = Async[Task]
    val race = F.deferred[Unit].flatMap { signal =>
      val childA = Task.delay { childAStarted = true }
        .flatMap(_ => Task.never[Unit])
        .onCancel {
          Task.delay { childAFinalizerWaiting = true }
            .flatMap(_ => signal.get)
            .flatMap(_ => Task.delay { childAFinalizerFinished = true })
        }

      val childB = Task.delay { childBStarted = true }
        .flatMap(_ => Task.never[Unit])
        .onCancel {
          signal.complete(())
            .flatMap(_ => Task.delay { childBFinalizerFinished = true })
        }

      F.racePair(childA, childB)
    }
    val started = race.start.unsafeRunToFuture()
    scheduler.tick()

    assert(started.value.exists(_.isSuccess))
    assert(childAStarted)
    assert(childBStarted)

    val canceled = started.value.get.get.cancel.unsafeRunToFuture()
    scheduler.tick()

    assert(childAFinalizerWaiting)
    assert(childAFinalizerFinished)
    assert(childBFinalizerFinished)
    assertEquals(canceled.value, Some(Success(())))
  }

  test("delay and defer are lazy, repeatable, and convert thrown errors") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var delayRuns = 0
    var deferRuns = 0

    val delayed = Task.delay {
      delayRuns += 1
      delayRuns
    }
    val deferred = Task.defer {
      deferRuns += 1
      Task.pure(deferRuns)
    }
    val repeated = for {
      delayValue <- delayed
      deferValue <- deferred
    } yield (delayValue, deferValue)

    assertEquals(delayRuns, 0)
    assertEquals(deferRuns, 0)

    val first = repeated.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(first.value, Some(Success((1, 1))))

    val second = repeated.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(second.value, Some(Success((2, 2))))

    val error = new RuntimeException("delayed failure")
    val recovered = for {
      delayValue <- Task.delay[Int](throw error).handleErrorWith(e => Task.pure(if (e eq error) 21 else 0))
      deferValue <- Task.defer[Int](throw error).handleErrorWith(e => Task.pure(if (e eq error) 21 else 0))
    } yield delayValue + deferValue
    val result = recovered.unsafeRunToFuture()
    scheduler.tick()

    assertEquals(result.value, Some(Success(42)))
  }

  test("synchronous defer loops yield at the configured batch size") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.BatchedExecution(32))
    var completed = false

    def loop(remaining: Int): Task[Unit] =
      if (remaining == 0) Task.delay { completed = true } else Task.defer(loop(remaining - 1))

    val result = loop(10000).unsafeRunToFuture()

    assert(scheduler.tickOne())
    assert(!completed)
    assertEquals(result.value, None)

    scheduler.tick()

    assert(completed)
    assertEquals(result.value, Some(Success(())))
  }

  test("evalOn restores the source scheduler after an error") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val error = new RuntimeException("target failure")
    var recoveryScheduler: ExecutionContext = null
    val result = Async[Task]
      .evalOn(Task.raiseError[Int](error), target)
      .handleErrorWith { _ =>
        Async[Task].executionContext.flatMap { ec =>
          Task.delay {
            recoveryScheduler = ec
            42
          }
        }
      }
      .unsafeRunToFuture()

    tick(source, target)

    assert(recoveryScheduler eq source)
    assertEquals(result.value, Some(Success(42)))
  }

  test("evalOn cancellation finalizers use their enclosing execution contexts") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var innerScheduler: ExecutionContext = null
    var outerScheduler: ExecutionContext = null
    val F = Async[Task]

    val effect = F
      .evalOn(
        Task.never[Unit].onCancel {
          F.executionContext.flatMap { ec =>
            Task.delay { innerScheduler = ec }
          }
        },
        target
      )
      .onCancel {
        F.executionContext.flatMap { ec =>
          Task.delay { outerScheduler = ec }
        }
      }

    val started = effect.start.unsafeRunToFuture()
    source.tick()
    target.tick()
    source.tick()

    assert(started.value.exists(_.isSuccess))
    val canceled = started.value.get.get.cancel.unsafeRunToFuture()
    tick(source, target)

    assertEquals(canceled.value, Some(Success(())))
    assert(innerScheduler eq target)
    assert(outerScheduler eq source)
  }

  test("evalOn preserves a plain execution context in the current fiber") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val ec = plainExecutionContext(target)
    val result = Async[Task]
      .evalOn(Async[Task].executionContext, ec)
      .unsafeRunToFuture()

    tickUntilComplete(source, target, result)

    assert(result.value.exists {
      case Success(actual) => actual eq ec
      case _ => false
    })
  }

  test("evalOn preserves a plain execution context in a started child fiber") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val ec = plainExecutionContext(target)
    val F = Async[Task]
    val childContext = F.executionContext
      .start
      .flatMap(_.join)
      .flatMap {
        case Outcome.Succeeded(value) => value
        case Outcome.Errored(error) => Task.raiseError[ExecutionContext](error)
        case Outcome.Canceled() =>
          Task.raiseError[ExecutionContext](new AssertionError("started child was canceled"))
      }
    val result = F.evalOn(childContext, ec).unsafeRunToFuture()

    tickUntilComplete(source, target, result)

    assert(result.value.exists {
      case Success(actual) => actual eq ec
      case _ => false
    })
  }

  private def assertCancellationFinalizerRetainsOwnership(
    signal: Callback[Throwable, Unit] => Unit
  ): Unit = {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var resumeBody: () => Unit = () => ()
    var resumeFinalizer: () => Unit = () => ()
    var finalizerRegistered = false
    var finalizerContinued = false

    val started = Task
      .async[Unit] { callback =>
        resumeBody = () => signal(callback)
      }
      .onCancel {
        Task.async[Unit] { callback =>
          finalizerRegistered = true
          resumeFinalizer = () => callback.onSuccess(())
        }.flatMap(_ => Task.delay { finalizerContinued = true })
      }
      .start
      .unsafeRunToFuture()

    scheduler.tick()

    assert(started.value.exists(_.isSuccess))
    val canceled = started.value.get.get.cancel.unsafeRunToFuture()
    scheduler.tick()

    assert(finalizerRegistered)
    assertEquals(canceled.value, None)

    resumeBody()
    scheduler.tick()

    assert(!finalizerContinued)
    assertEquals(canceled.value, None)

    resumeFinalizer()
    scheduler.tick()

    assert(finalizerContinued)
    assertEquals(canceled.value, Some(Success(())))
  }

  private def tick(source: TestScheduler, target: TestScheduler): Unit = {
    source.tick()
    target.tick()
    source.tick()
    target.tick()
    source.tick()
  }

  private def tickUntilComplete[A](
    source: TestScheduler,
    target: TestScheduler,
    result: Future[A]
  ): Unit = {
    var remaining = 10
    while (result.value.isEmpty && remaining > 0) {
      source.tick()
      target.tick()
      remaining -= 1
    }
  }

  private def plainExecutionContext(target: TestScheduler): ExecutionContext =
    new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        target.execute(runnable)

      override def reportFailure(cause: Throwable): Unit =
        target.reportFailure(cause)
    }
}
