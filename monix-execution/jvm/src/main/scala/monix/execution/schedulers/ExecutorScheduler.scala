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

package monix.execution.schedulers

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executor, ExecutorService, ScheduledExecutorService}

import monix.execution.internal.forkJoin.{AdaptedForkJoinPool, DynamicWorkerThreadFactory, StandardWorkerThreadFactory}
import monix.execution.misc.NonFatal
import monix.execution.{Cancelable, Scheduler, UncaughtExceptionReporter}

import scala.concurrent.{Future, Promise, blocking}
// Prevents conflict with the deprecated symbol
import monix.execution.{ExecutionModel => ExecModel}
import scala.concurrent.duration.TimeUnit

/** An [[ExecutorScheduler]] is a class for building a
  * [[monix.execution.schedulers.SchedulerService SchedulerService]]
  * out of a Java `ExecutorService`.
  */
abstract class ExecutorScheduler(e: ExecutorService, r: UncaughtExceptionReporter)
  extends SchedulerService with ReferenceScheduler with BatchingScheduler {

  /** Returns the underlying `ExecutorService` reference. */
  def executor: ExecutorService = e

  override final protected def executeAsync(r: Runnable): Unit =
    e.execute(r)
  override final def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)
  override final def isShutdown: Boolean =
    e.isShutdown
  override final def isTerminated: Boolean =
    e.isTerminated
  override final def shutdown(): Unit =
    e.shutdown()

  override final def awaitTermination(timeout: Long, unit: TimeUnit, awaitOn: Scheduler): Future[Boolean] = {
    val p = Promise[Boolean]()
    awaitOn.execute(new Runnable {
      override def run() =
        try blocking {
          p.success(e.awaitTermination(timeout, unit))
        }
        catch {
          case NonFatal(ex) =>
            p.failure(ex)
        }
    })
    p.future
  }

  override def withExecutionModel(em: ExecModel): SchedulerService =
    throw new NotImplementedError("ExecutorService.withExecutionModel")
}

object ExecutorScheduler {
  /** Builder for an [[ExecutorScheduler]], converting a
    * Java `ScheduledExecutorService`.
    *
    * @param service is the Java `ScheduledExecutorService` that will take
    *        care of scheduling and execution of all runnables.
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    * @param executionModel is the preferred
    *        [[monix.execution.ExecutionModel ExecutionModel]], a guideline
    *        for run-loops and producers of data.
    */
  def apply(
    service: ExecutorService,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel): ExecutorScheduler = {

    service match {
      case ref: ScheduledExecutorService =>
        new FromScheduledExecutor(ref, reporter, executionModel)
      case _ =>
        val s = Defaults.scheduledExecutor
        new FromSimpleExecutor(s, service, reporter, executionModel)
    }
  }

  /** Creates an [[ExecutorScheduler]] backed by a `ForkJoinPool`
    * that isn't integrated with Scala's `BlockContext`.
    */
  def forkJoinStatic(
    name: String,
    parallelism: Int,
    daemonic: Boolean,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel): ExecutorScheduler = {

    val handler = new UncaughtExceptionHandler {
      def uncaughtException(t: Thread, e: Throwable) =
        reporter.reportFailure(e)
    }

    val pool = new AdaptedForkJoinPool(
      parallelism,
      new StandardWorkerThreadFactory(name, handler, daemonic),
      handler,
      asyncMode = true
    )

    apply(pool, reporter, executionModel)
  }

  /** Creates an [[ExecutorScheduler]] backed by a `ForkJoinPool`
    * integrated with Scala's `BlockContext`.
    */
  def forkJoinDynamic(
    name: String,
    parallelism: Int,
    maxThreads: Int,
    daemonic: Boolean,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel): ExecutorScheduler = {

    val exceptionHandler = new UncaughtExceptionHandler {
      def uncaughtException(t: Thread, e: Throwable) =
        reporter.reportFailure(e)
    }

    val pool = new AdaptedForkJoinPool(
      parallelism,
      new DynamicWorkerThreadFactory(name, maxThreads, exceptionHandler, daemonic),
      exceptionHandler,
      asyncMode = true
    )

    apply(pool, reporter, executionModel)
  }

  /** Converts a Java `ExecutorService`.
    *
    * In such a case, given that an `ExecutorService` has no ability to
    * schedule executions with a delay, we have to fallback to another
    * `ScheduledExecutorService` instance, which will usually be
    * Monix's default.
    */
  private final class FromSimpleExecutor(
    scheduler: ScheduledExecutorService,
    executor: ExecutorService,
    r: UncaughtExceptionReporter,
    val executionModel: ExecModel)
    extends ExecutorScheduler(executor, r) {

    override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      if (initialDelay <= 0) {
        executor.execute(r)
        Cancelable.empty
      } else {
        val deferred = new DeferredRunnable(r, executor)
        val task = scheduler.schedule(deferred, initialDelay, unit)
        Cancelable(() => task.cancel(true))
      }
    }

    override def withExecutionModel(em: ExecModel): SchedulerService =
      new FromSimpleExecutor(scheduler, executor, r, em)
  }

  /** Converts a Java `ScheduledExecutorService`. */
  private final class FromScheduledExecutor(
    s: ScheduledExecutorService,
    r: UncaughtExceptionReporter,
    override val executionModel: ExecModel)
    extends ExecutorScheduler(s, r) {

    override def executor: ScheduledExecutorService = s

    def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      if (initialDelay <= 0) {
        execute(r)
        Cancelable.empty
      }
      else {
        val task = s.schedule(r, initialDelay, unit)
        Cancelable(() => task.cancel(true))
      }
    }

    override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      val task = s.scheduleWithFixedDelay(r, initialDelay, delay, unit)
      Cancelable(() => task.cancel(false))
    }

    override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      val task = s.scheduleAtFixedRate(r, initialDelay, period, unit)
      Cancelable(() => task.cancel(false))
    }

    override def withExecutionModel(em: ExecModel): SchedulerService =
      new FromScheduledExecutor(s, r, em)
  }

  /** Runnable that defers the execution of the given runnable to the
    * given execution context.
    */
  private class DeferredRunnable(r: Runnable, e: Executor) extends Runnable {
    def run(): Unit = e.execute(r)
  }
}
