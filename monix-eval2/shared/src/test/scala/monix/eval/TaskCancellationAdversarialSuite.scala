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
import monix.execution.{ CompletionCallback, ExecutionModel, Scheduler }
import monix.execution.schedulers.TestScheduler
import java.util.concurrent.RejectedExecutionException
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object TaskCancellationAdversarialSuite extends SimpleTestSuite {
  test("a rejected joiner does not prevent healthy joiners from resuming") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejectedScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var completeTarget: CompletionCallback[Unit] = null
    val started = Task
      .fromAsync[Unit](callback => completeTarget = callback)
      .start
      .unsafeRunToFuture()
    drain(source)

    assert(started.value.exists(_.isSuccess))
    val targetFiber = started.value.get.get
    assert(completeTarget ne null)

    var rejectSubmissions = false
    var acceptedSubmissions = 0
    val rejectingContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        if (rejectSubmissions)
          throw new RejectedExecutionException("joiner executor stopped")
        else {
          acceptedSubmissions += 1
          rejectedScheduler.execute(runnable)
        }

      override def reportFailure(cause: Throwable): Unit =
        rejectedScheduler.reportFailure(cause)
    }

    val rejectedJoiner = Async[Task]
      .evalOn(targetFiber.join, rejectingContext)
      .start
      .unsafeRunToFuture()
    drainBoth(source, rejectedScheduler)

    assert(rejectedJoiner.value.exists(_.isSuccess))
    assert(acceptedSubmissions > 0)

    var healthyOutcome: Outcome[Task, Throwable, Unit] = null
    val healthyJoiner = targetFiber.join
      .flatMap(outcome => Task.delay { healthyOutcome = outcome })
      .unsafeRunToFuture()
    drain(source)

    assertEquals(healthyOutcome, null)
    rejectSubmissions = true
    completeTarget.onSuccess(())
    drain(source)

    assert(healthyOutcome ne null)
    assert(healthyOutcome match {
      case Outcome.Succeeded(_) => true
      case _ => false
    })
    assertEquals(healthyJoiner.value, Some(Success(())))
    assert(rejectedScheduler.state.lastReportedError.isInstanceOf[RejectedExecutionException])
  }

  test("a rejected Deferred waiter does not prevent healthy waiters from resuming") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejectedScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    val deferredFuture = F.deferred[Unit].unsafeRunToFuture()
    drain(source)

    assert(deferredFuture.value.exists(_.isSuccess))
    val deferred = deferredFuture.value.get.get

    var rejectSubmissions = false
    var reportedError: Throwable = null
    val rejectingContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        if (rejectSubmissions)
          throw new RejectedExecutionException("deferred waiter executor stopped")
        else
          rejectedScheduler.execute(runnable)

      override def reportFailure(cause: Throwable): Unit = {
        reportedError = cause
        rejectedScheduler.reportFailure(cause)
      }
    }

    val rejectedWaiter = F.evalOn(deferred.get, rejectingContext).start.unsafeRunToFuture()
    drainBoth(source, rejectedScheduler)
    assert(rejectedWaiter.value.exists(_.isSuccess))

    var healthyWaiterCompleted = false
    val healthyWaiter = deferred.get
      .flatMap(_ => Task.delay { healthyWaiterCompleted = true })
      .unsafeRunToFuture()
    drain(source)
    assert(!healthyWaiterCompleted)

    rejectSubmissions = true
    val completion = deferred.complete(()).unsafeRunToFuture()
    drain(source)

    assert(
      completion.value.contains(Success(true)) && healthyWaiterCompleted,
      s"completion=${completion.value}, healthyWaiterCompleted=$healthyWaiterCompleted, " +
        s"rejectingReporter=$reportedError"
    )
    assertEquals(healthyWaiter.value, Some(Success(())))
    assert(reportedError.isInstanceOf[RejectedExecutionException])
  }

  test("a rejected evalOn shift fails on the source and runs its finalizer there") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejectedScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejection = new RejectedExecutionException("target executor stopped")
    val rejectedContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = throw rejection

      override def reportFailure(cause: Throwable): Unit =
        rejectedScheduler.reportFailure(cause)
    }
    var targetEffectRan = false
    var finalizerRan = false
    var finalizerContext: ExecutionContext = null
    val F = Async[Task]
    val sourceEffect = F.evalOn(
      Task.delay {
        targetEffectRan = true
        42
      },
      rejectedContext
    )
    val effect = F.guarantee(
      sourceEffect,
      F.executionContext.flatMap { ec =>
        Task.delay {
          finalizerContext = ec
          finalizerRan = true
        }
      }
    )
    val result = effect.unsafeRunToFuture()
    drainBoth(source, rejectedScheduler)

    assert(result.value.exists {
      case Failure(error) => error eq rejection
      case _ => false
    })
    assert(finalizerRan)
    assert(finalizerContext eq source)
    assert(!targetEffectRan)
  }

  test("a rejected batch resubmission preserves the error finalizer on its scheduler") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val targetQueue = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejection = new RejectedExecutionException("target batch resubmission rejected")
    var bodyEntered = false
    val targetContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        if (bodyEntered) throw rejection
        else targetQueue.execute(runnable)

      override def reportFailure(cause: Throwable): Unit =
        targetQueue.reportFailure(cause)
    }
    val targetScheduler = Scheduler(targetContext, ExecutionModel.AlwaysAsyncExecution)
    var finalizerRuns = 0
    var finalizerContext: ExecutionContext = null
    val F = Async[Task]
    val targetEffect = F.guarantee(
      Task.delay {
        bodyEntered = true
        42
      },
      F.executionContext.flatMap { ec =>
        Task.delay {
          finalizerContext = ec
          finalizerRuns += 1
        }
      }
    )
    val effect = F.evalOn(targetEffect, targetScheduler)
    val result = effect.unsafeRunToFuture()
    drainBoth(source, targetQueue)

    assert(bodyEntered)
    assert(result.value.exists {
      case Failure(error) => error eq rejection
      case _ => false
    })
    assertEquals(finalizerRuns, 1)
    assert(finalizerContext eq targetScheduler)
  }

  test("a rejected child submission fails the parent and runs its finalizer") {
    val parentQueue = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejection = new RejectedExecutionException("child submission rejected")
    var submissions = 0
    val parentContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = {
        submissions += 1
        if (submissions == 2) throw rejection
        else parentQueue.execute(runnable)
      }

      override def reportFailure(cause: Throwable): Unit =
        parentQueue.reportFailure(cause)
    }
    val parentScheduler = Scheduler(parentContext, ExecutionModel.SynchronousExecution)
    implicit val source: Scheduler = parentScheduler
    val F = Async[Task]
    var childEntered = false
    var finalizerRuns = 0
    var finalizerContext: ExecutionContext = null
    val effect = F.guarantee(
      Task.delay { childEntered = true }.start.map(_ => ()),
      F.executionContext.flatMap { ec =>
        Task.delay {
          finalizerContext = ec
          finalizerRuns += 1
        }
      }
    )
    val result = effect.unsafeRunToFuture()
    drain(parentQueue)

    assertEquals(submissions, 2)
    assert(
      result.value.exists {
        case Failure(error) => error eq rejection
        case _ => false
      },
      s"result=${result.value}, childEntered=$childEntered, finalizerRuns=$finalizerRuns, " +
        s"finalizerContext=$finalizerContext, submissions=$submissions"
    )
    assert(!childEntered)
    assertEquals(finalizerRuns, 1)
    assert(finalizerContext eq parentScheduler)
  }

  test("target and origin resubmission rejection preserves the target error and finalizer") {
    val sourceRejection = new RejectedExecutionException("origin rejected resubmission")
    val targetRejection = new RejectedExecutionException("target rejected evalOn")
    var sourceSubmissions = 0
    var finalizerRuns = 0
    var bodyRuns = 0
    val sourceContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = {
        sourceSubmissions += 1
        if (sourceSubmissions == 1) runnable.run()
        else throw sourceRejection
      }

      override def reportFailure(cause: Throwable): Unit = ()
    }
    val targetContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = throw targetRejection

      override def reportFailure(cause: Throwable): Unit = ()
    }
    implicit val source: Scheduler = Scheduler(sourceContext, ExecutionModel.BatchedExecution(1024))
    val targetScheduler = Scheduler(targetContext, ExecutionModel.SynchronousExecution)
    val F = Async[Task]
    val guarded = F.guarantee(
      F.evalOn(Task.delay { bodyRuns += 1 }, targetScheduler),
      F.executionContext.flatMap { _ =>
        Task.delay { finalizerRuns += 1 }
      }
    )
    val result = guarded.unsafeRunToFuture()

    assert(
      result.value.exists {
        case Failure(error) => error eq targetRejection
        case _ => false
      },
      s"result=${result.value}, sourceSubmissions=$sourceSubmissions"
    )
    assertEquals(sourceSubmissions, 2)
    assertEquals(bodyRuns, 0)
    assertEquals(finalizerRuns, 1)
  }

  test("a rejected cancellation submission reports its error without running the finalizer") {
    implicit val source: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val targetQueue = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val rejection = new RejectedExecutionException("target stopped before cancellation")
    var rejectTarget = false
    var registered = false
    var finalizerRuns = 0
    val targetContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        if (rejectTarget) throw rejection
        else targetQueue.execute(runnable)

      override def reportFailure(cause: Throwable): Unit = targetQueue.reportFailure(cause)
    }
    val targetScheduler = Scheduler(targetContext, ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    val suspended = Task.fromAsync[Unit] { _ => registered = true }
      .onCancel(Task.delay { finalizerRuns += 1 })
    val started = F.evalOn(suspended.start, targetScheduler).unsafeRunToFuture()
    drainBoth(source, targetQueue)

    assert(registered)
    assert(started.value.exists(_.isSuccess))
    val fiber = started.value.get.get
    rejectTarget = true
    val canceled = fiber.cancel.unsafeRunToFuture()
    drainBoth(source, targetQueue)

    assert(canceled.value.exists {
      case Failure(error) => error eq rejection
      case _ => false
    })
    assertEquals(finalizerRuns, 0)
  }

  private def drain(scheduler: TestScheduler, maxSteps: Int = 256): Unit = {
    var steps = 0
    var hasWork = true
    while (hasWork && steps < maxSteps) {
      hasWork = scheduler.tickOne()
      if (hasWork)
        steps += 1
    }
    assert(!hasWork, s"scheduler did not become idle after $maxSteps steps")
  }

  private def drainBoth(
    first: TestScheduler,
    second: TestScheduler,
    maxSteps: Int = 256
  ): Unit = {
    var steps = 0
    var hasWork = true
    while (hasWork && steps < maxSteps) {
      val firstHasWork = first.tickOne()
      val secondHasWork = second.tickOne()
      hasWork = firstHasWork || secondHasWork
      if (hasWork)
        steps += 1
    }
    assert(!hasWork, s"schedulers did not become idle after $maxSteps steps")
  }
}
