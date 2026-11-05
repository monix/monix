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

import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference, LongAdder }
import java.util.concurrent.{ CountDownLatch, TimeUnit }

import minitest.SimpleTestSuite
import monix.execution.schedulers.SchedulerService
import monix.execution.{ CompletionCallback, Scheduler, TestUtils, UncaughtExceptionReporter }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object TaskCallbackSafetyJVMSuite extends SimpleTestSuite with TestUtils {
  val WORKERS = 10
  val RETRIES = if (!isCI) 1000 else 100

  test("IO.async accepts exactly one concurrent callback") {
    implicit val scheduler: SchedulerService =
      Scheduler.io("io-callback-safety")

    try {
      for (_ <- 0 until RETRIES) {
        val accepted = new AtomicInteger(0)
        val completed = new CountDownLatch(1)
        val result = Task
          .fromAsync[Int] { callback =>
            runConcurrently(scheduler) {
              if (callback.tryOnSuccess(42))
                accepted.set(1)
            }
          }
          .unsafeRunToFuture()

        result.onComplete(_ => completed.countDown())

        await(completed)
        assertEquals(accepted.get(), 1)
        assertEquals(result.value, Some(Success(42)))
      }
    } finally {
      scheduler.shutdown()
      assert(scheduler.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  test("IO.async callback can race run-loop suspension") {
    implicit val scheduler: SchedulerService =
      Scheduler.io("io-callback-suspension-race")

    try {
      for (_ <- 0 until RETRIES) {
        val workersReady = new CountDownLatch(WORKERS)
        val releaseWorkers = new CountDownLatch(1)
        val workersFinished = new CountDownLatch(WORKERS)
        val completed = new CountDownLatch(1)
        val accepted = new AtomicInteger(0)

        val result = Task
          .fromAsync[Int] { callback =>
            for (_ <- 0 until WORKERS) {
              scheduler.execute { () =>
                workersReady.countDown()
                await(releaseWorkers)
                try {
                  if (callback.tryOnSuccess(42))
                    accepted.set(1)
                } finally {
                  workersFinished.countDown()
                }
              }
            }

            await(workersReady)
            releaseWorkers.countDown()
          }
          .unsafeRunToFuture()

        result.onComplete(_ => completed.countDown())

        await(workersFinished)
        await(completed)
        assertEquals(accepted.get(), 1)
        assertEquals(result.value, Some(Success(42)))
      }
    } finally {
      scheduler.shutdown()
      assert(scheduler.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  test("fiber completion does not lose concurrent joiners") {
    implicit val scheduler: SchedulerService =
      Scheduler.io("io-fiber-join-race")

    try {
      for (_ <- 0 until RETRIES) {
        val registered = new CountDownLatch(1)
        val resume = new AtomicReference[CompletionCallback[Int]]()
        val fiber = Await.result(
          Task
            .fromAsync[Int] { callback =>
              resume.set(callback)
              registered.countDown()
            }
            .start
            .unsafeRunToFuture(),
          10.seconds
        )

        await(registered)

        val racersReady = new CountDownLatch(WORKERS + 1)
        val releaseRacers = new CountDownLatch(1)
        val joinsCompleted = new CountDownLatch(WORKERS)
        val successfulJoins = new LongAdder()
        val failedJoins = new LongAdder()

        for (_ <- 0 until WORKERS) {
          scheduler.execute { () =>
            racersReady.countDown()
            await(releaseRacers)
            fiber.join.unsafeRunToFuture().onComplete {
              case Success(outcome) =>
                if (outcome.isSuccess) successfulJoins.increment()
                else failedJoins.increment()
                joinsCompleted.countDown()
              case Failure(_) =>
                failedJoins.increment()
                joinsCompleted.countDown()
            }
          }
        }

        scheduler.execute { () =>
          racersReady.countDown()
          await(releaseRacers)
          resume.get().onSuccess(42)
        }

        await(racersReady)
        releaseRacers.countDown()
        await(joinsCompleted)

        assertEquals(successfulJoins.intValue(), WORKERS)
        assertEquals(failedJoins.intValue(), 0)
      }
    } finally {
      scheduler.shutdown()
      assert(scheduler.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  test("fiber cancellation can race async completion") {
    implicit val scheduler: SchedulerService =
      Scheduler.io("io-fiber-cancel-race")

    try {
      for (_ <- 0 until RETRIES) {
        val registered = new CountDownLatch(1)
        val resume = new AtomicReference[CompletionCallback[Int]]()
        val finalizerRuns = new LongAdder()
        val fiber = Await.result(
          Task
            .fromAsync[Int] { callback =>
              resume.set(callback)
              registered.countDown()
            }
            .onCancel(Task.delay(finalizerRuns.increment()))
            .start
            .unsafeRunToFuture(),
          10.seconds
        )

        await(registered)

        val racersReady = new CountDownLatch(2)
        val releaseRacers = new CountDownLatch(1)
        val cancelCompleted = new CountDownLatch(1)
        val cancelError = new AtomicReference[Throwable]()

        scheduler.execute { () =>
          racersReady.countDown()
          await(releaseRacers)
          fiber.cancel.unsafeRunToFuture().onComplete {
            case Success(_) =>
              cancelCompleted.countDown()
            case Failure(error) =>
              cancelError.set(error)
              cancelCompleted.countDown()
          }
        }
        scheduler.execute { () =>
          racersReady.countDown()
          await(releaseRacers)
          resume.get().onSuccess(42)
        }

        await(racersReady)
        releaseRacers.countDown()

        val outcome = Await.result(fiber.join.unsafeRunToFuture(), 10.seconds)
        await(cancelCompleted)

        assertEquals(cancelError.get(), null)
        if (outcome.isCanceled)
          assertEquals(finalizerRuns.intValue(), 1)
        else {
          assert(outcome.isSuccess)
          assertEquals(finalizerRuns.intValue(), 0)
        }
      }
    } finally {
      scheduler.shutdown()
      assert(scheduler.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  test("original async completion can race a suspended cancellation finalizer") {
    val reportedFailure = new AtomicReference[Throwable]()
    implicit val scheduler: SchedulerService =
      Scheduler.io(
        "io-finalizer-callback-race",
        reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
      )

    try {
      for (_ <- 0 until RETRIES) {
        val originalRegistered = new CountDownLatch(1)
        val finalizerRegistered = new CountDownLatch(1)
        val originalResume = new AtomicReference[CompletionCallback[Int]]()
        val finalizerResume = new AtomicReference[CompletionCallback[Unit]]()
        val finalizerContinuations = new AtomicInteger(0)

        val fiber = Await.result(
          Task
            .fromAsync[Int] { callback =>
              originalResume.set(callback)
              originalRegistered.countDown()
            }
            .onCancel(
              Task
                .fromAsync[Unit] { callback =>
                  finalizerResume.set(callback)
                  finalizerRegistered.countDown()
                }
                .flatMap(_ => Task.delay { finalizerContinuations.incrementAndGet(); () })
            )
            .start
            .unsafeRunToFuture(),
          10.seconds
        )

        await(originalRegistered)

        val cancelCompleted = new CountDownLatch(1)
        val cancelError = new AtomicReference[Throwable]()
        fiber.cancel.unsafeRunToFuture().onComplete {
          case Success(_) =>
            cancelCompleted.countDown()
          case Failure(error) =>
            cancelError.set(error)
            cancelCompleted.countDown()
        }

        await(finalizerRegistered)

        val racersReady = new CountDownLatch(2)
        val releaseRacers = new CountDownLatch(1)
        val racersFinished = new CountDownLatch(2)
        val acceptedOriginal = new AtomicInteger(0)
        val acceptedFinalizer = new AtomicInteger(0)

        scheduler.execute { () =>
          racersReady.countDown()
          await(releaseRacers)
          try {
            if (originalResume.get().tryOnSuccess(42)) {
              val _ = acceptedOriginal.incrementAndGet()
            }
          } finally {
            racersFinished.countDown()
          }
        }
        scheduler.execute { () =>
          racersReady.countDown()
          await(releaseRacers)
          try {
            if (finalizerResume.get().tryOnSuccess(())) {
              val _ = acceptedFinalizer.incrementAndGet()
            }
          } finally {
            racersFinished.countDown()
          }
        }

        await(racersReady)
        releaseRacers.countDown()
        await(racersFinished)
        await(cancelCompleted)

        assertEquals(acceptedOriginal.get(), 1)
        assertEquals(acceptedFinalizer.get(), 1)
        assertEquals(cancelError.get(), null)
        assertEquals(finalizerContinuations.get(), 1)
        assertEquals(reportedFailure.get(), null)
      }
    } finally {
      scheduler.shutdown()
      assert(scheduler.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  test("original async completion leaves a cancellation finalizer pending") {
    val reportedFailure = new AtomicReference[Throwable]()
    implicit val scheduler: SchedulerService =
      Scheduler.singleThread(
        "io-finalizer-callback-pending",
        reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
      )

    try {
      for (_ <- 0 until RETRIES) {
        val originalRegistered = new CountDownLatch(1)
        val finalizerRegistered = new CountDownLatch(1)
        val originalResume = new AtomicReference[CompletionCallback[Int]]()
        val finalizerResume = new AtomicReference[CompletionCallback[Unit]]()
        val finalizerContinuations = new AtomicInteger(0)

        val fiber = Await.result(
          Task
            .fromAsync[Int] { callback =>
              originalResume.set(callback)
              originalRegistered.countDown()
            }
            .onCancel(
              Task
                .fromAsync[Unit] { callback =>
                  finalizerResume.set(callback)
                  finalizerRegistered.countDown()
                }
                .flatMap(_ => Task.delay { finalizerContinuations.incrementAndGet(); () })
            )
            .start
            .unsafeRunToFuture(),
          10.seconds
        )

        await(originalRegistered)

        val cancelCompleted = new CountDownLatch(1)
        val cancelError = new AtomicReference[Throwable]()
        fiber.cancel.unsafeRunToFuture().onComplete {
          case Success(_) =>
            cancelCompleted.countDown()
          case Failure(error) =>
            cancelError.set(error)
            cancelCompleted.countDown()
        }

        await(finalizerRegistered)
        assert(originalResume.get().tryOnSuccess(42))

        // The signal is asynchronous. Two scheduler turns ensure its run-loop
        // continuation has run before checking that the finalizer is still waiting.
        val originalProcessed = new CountDownLatch(1)
        scheduler.execute(() => scheduler.execute(() => originalProcessed.countDown()))
        await(originalProcessed)

        assertEquals(finalizerContinuations.get(), 0)
        assertEquals(cancelCompleted.getCount, 1L)

        assert(finalizerResume.get().tryOnSuccess(()))
        await(cancelCompleted)

        assertEquals(cancelError.get(), null)
        assertEquals(finalizerContinuations.get(), 1)
        assertEquals(reportedFailure.get(), null)
      }
    } finally {
      scheduler.shutdown()
      assert(scheduler.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  private def runConcurrently(scheduler: Scheduler)(f: => Unit): Unit = {
    val workersStarted = new CountDownLatch(WORKERS)
    val workersFinished = new CountDownLatch(WORKERS)

    for (_ <- 0 until WORKERS) {
      scheduler.execute { () =>
        workersStarted.countDown()
        try f
        finally workersFinished.countDown()
      }
    }

    await(workersStarted)
    await(workersFinished)
  }

  private def await(latch: CountDownLatch): Unit = {
    val seconds = 10
    assert(
      latch.await(seconds.toLong, TimeUnit.SECONDS),
      s"latch.await($seconds seconds)"
    )
  }
}
