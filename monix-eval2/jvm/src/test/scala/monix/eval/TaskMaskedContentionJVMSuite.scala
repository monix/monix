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

import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import java.util.concurrent.{ CountDownLatch, ExecutorService, Executors, TimeUnit }

import cats.effect.kernel.{ Async, Outcome }
import minitest.SimpleTestSuite
import monix.execution.schedulers.SchedulerService
import monix.execution.{ Callback, Scheduler, TestUtils, UncaughtExceptionReporter }

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object TaskMaskedContentionJVMSuite extends SimpleTestSuite with TestUtils {
  private val CANCELERS = 4
  private val RETRIES = if (!isCI) 1000 else 100

  test("masked callback racing multiple cancels waits for its finalizer") {
    val reportedFailure = new AtomicReference[Throwable]()
    val fiberScheduler = Scheduler.io(
      "io-masked-cancel-race",
      reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
    )
    val cancelScheduler = Scheduler.io(
      "io-masked-cancel-racers",
      reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
    )
    val workers = Executors.newFixedThreadPool(CANCELERS + 1)

    try {
      for (_ <- 0 until RETRIES)
        runRound(fiberScheduler, cancelScheduler, None, workers, reportedFailure)
    } finally {
      workers.shutdown()
      assert(workers.awaitTermination(10, TimeUnit.SECONDS), "workers.awaitTermination")
      cancelScheduler.shutdown()
      assert(cancelScheduler.awaitTermination(10.seconds), "cancelScheduler.awaitTermination")
      fiberScheduler.shutdown()
      assert(fiberScheduler.awaitTermination(10.seconds), "fiberScheduler.awaitTermination")
    }
  }

  test("masked callback contention across evalOn restores both schedulers") {
    val reportedFailure = new AtomicReference[Throwable]()
    val fiberScheduler = Scheduler.singleThread(
      "io-masked-evalOn-source",
      reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
    )
    val targetScheduler = Scheduler.singleThread(
      "io-masked-evalOn-target",
      reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
    )
    val cancelScheduler = Scheduler.io(
      "io-masked-evalOn-cancel-racers",
      reporter = UncaughtExceptionReporter(error => reportedFailure.set(error))
    )
    val workers = Executors.newFixedThreadPool(CANCELERS + 1)

    try {
      for (_ <- 0 until RETRIES)
        runRound(fiberScheduler, cancelScheduler, Some(targetScheduler), workers, reportedFailure)
    } finally {
      workers.shutdown()
      assert(workers.awaitTermination(10, TimeUnit.SECONDS), "workers.awaitTermination")
      cancelScheduler.shutdown()
      assert(cancelScheduler.awaitTermination(10.seconds), "cancelScheduler.awaitTermination")
      targetScheduler.shutdown()
      assert(targetScheduler.awaitTermination(10.seconds), "targetScheduler.awaitTermination")
      fiberScheduler.shutdown()
      assert(fiberScheduler.awaitTermination(10.seconds), "fiberScheduler.awaitTermination")
    }
  }

  private def runRound(
    fiberScheduler: SchedulerService,
    cancelScheduler: SchedulerService,
    targetScheduler: Option[SchedulerService],
    workers: ExecutorService,
    reportedFailure: AtomicReference[Throwable]
  ): Unit = {
    val F = Async[Task]
    val bodyRegistered = new CountDownLatch(1)
    val finalizerRegistered = new CountDownLatch(1)
    val resumeBody = new AtomicReference[Callback[Throwable, Int]]()
    val resumeFinalizer = new AtomicReference[Callback[Throwable, Unit]]()
    val consumedValues = new AtomicInteger(0)
    val consumedValue = new AtomicInteger(-1)
    val finalizerContinuations = new AtomicInteger(0)
    val outerFinalizers = new AtomicInteger(0)
    val innerContext = new AtomicReference[ExecutionContext]()
    val outerContext = new AtomicReference[ExecutionContext]()
    val cancelError = new AtomicReference[Throwable]()

    val wait = Task.async[Int] { callback =>
      resumeBody.set(callback)
      bodyRegistered.countDown()
    }.flatMap { value =>
      Task.delay {
        consumedValue.set(value)
        val _ = consumedValues.incrementAndGet()
      }
    }

    val finalizer = F.executionContext
      .flatMap(ec => Task.delay { innerContext.set(ec); () })
      .flatMap { _ =>
        Task.async[Unit] { callback =>
          resumeFinalizer.set(callback)
          finalizerRegistered.countDown()
        }
      }
      .flatMap(_ => Task.delay { val _ = finalizerContinuations.incrementAndGet() })

    val masked = F.uncancelable { poll =>
      wait.flatMap(_ => poll(Task.never[Unit]))
    }.onCancel(finalizer)

    val onTarget = targetScheduler match {
      case Some(target) => F.evalOn(masked, target)
      case None => masked
    }
    val effect = onTarget.onCancel {
      F.executionContext
        .flatMap(ec => Task.delay { outerContext.set(ec); () })
        .flatMap(_ => Task.delay { val _ = outerFinalizers.incrementAndGet() })
    }

    val fiber = Await.result(effect.start.unsafeRunToFuture()(fiberScheduler), 10.seconds)
    await(bodyRegistered)

    val cancellationsCompleted = new CountDownLatch(CANCELERS)
    val cancellationRequestsCreated = new CountDownLatch(CANCELERS)
    val racedTasksCompleted = new CountDownLatch(CANCELERS + 1)
    val racersReady = new CountDownLatch(CANCELERS + 1)
    val releaseRacers = new CountDownLatch(1)
    val acceptedBodyCallback = new AtomicInteger(0)
    val workerFailure = new AtomicReference[Throwable]()

    for (_ <- 0 until CANCELERS)
      workers.execute(() => {
        racersReady.countDown()
        try {
          await(releaseRacers)
          fiber.cancel.unsafeRunToFuture()(cancelScheduler).onComplete {
            case Success(_) =>
              cancellationsCompleted.countDown()
            case Failure(error) =>
              val _ = cancelError.compareAndSet(null, error)
              cancellationsCompleted.countDown()
          }(cancelScheduler)
          cancellationRequestsCreated.countDown()
        } catch {
          case error: Throwable =>
            captureWorkerFailure(workerFailure, error)
            cancellationRequestsCreated.countDown()
            cancellationsCompleted.countDown()
        } finally {
          racedTasksCompleted.countDown()
        }
      })

    workers.execute(() => {
      racersReady.countDown()
      try {
        await(releaseRacers)
        val callback = resumeBody.get()
        if (callback.tryOnSuccess(42)) acceptedBodyCallback.set(1)
      } catch {
        case error: Throwable =>
          captureWorkerFailure(workerFailure, error)
      } finally {
        racedTasksCompleted.countDown()
      }
    })

    await(racersReady)
    releaseRacers.countDown()
    await(cancellationRequestsCreated)
    await(racedTasksCompleted)

    val joined = new CountDownLatch(1)
    val outcome = new AtomicReference[Outcome[Task, Throwable, Unit]]()
    fiber.join.unsafeRunToFuture()(fiberScheduler).onComplete {
      case Success(value) =>
        outcome.set(value)
        joined.countDown()
      case Failure(error) =>
        captureWorkerFailure(workerFailure, error)
        joined.countDown()
    }(fiberScheduler)

    try {
      await(finalizerRegistered)
      assertEquals(workerFailure.get(), null)
      assertEquals(acceptedBodyCallback.get(), 1)
      assertEquals(consumedValues.get(), 1)
      assertEquals(consumedValue.get(), 42)
      assertEquals(finalizerContinuations.get(), 0)
      assertEquals(outerFinalizers.get(), 0)
      assertEquals(cancellationsCompleted.getCount, CANCELERS.toLong)
      assertEquals(joined.getCount, 1L)

      val finalizerCallback = resumeFinalizer.get()
      assert(finalizerCallback ne null)
      assert(finalizerCallback.tryOnSuccess(()))
    } finally {
      val finalizerCallback = resumeFinalizer.get()
      if (finalizerCallback ne null) {
        val _ = finalizerCallback.tryOnSuccess(())
      }
    }

    await(cancellationsCompleted)
    await(joined)

    assertEquals(cancelError.get(), null)
    assertEquals(finalizerContinuations.get(), 1)
    assertEquals(outerFinalizers.get(), 1)
    assert(outcome.get().isCanceled)
    val expectedInnerContext: ExecutionContext = targetScheduler match {
      case Some(target) => target
      case None => fiberScheduler
    }
    assert(innerContext.get() eq expectedInnerContext)
    assert(outerContext.get() eq fiberScheduler)
    assertEquals(reportedFailure.get(), null)
  }

  private def await(latch: CountDownLatch): Unit = {
    val seconds = 10
    assert(latch.await(seconds.toLong, TimeUnit.SECONDS), s"latch.await($seconds seconds)")
  }

  private def captureWorkerFailure(
    target: AtomicReference[Throwable],
    error: Throwable
  ): Unit = {
    // Forward AssertionError to the test thread, which cannot catch worker exceptions directly.
    val _ = target.compareAndSet(null, error)
  }
}
