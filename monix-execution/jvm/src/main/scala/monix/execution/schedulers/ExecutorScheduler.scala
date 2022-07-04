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

package monix.execution.schedulers

import java.util.concurrent.{ ExecutorService, ScheduledExecutorService }
import monix.execution.internal.forkJoin.{
  AdaptedForkJoinPool,
  DynamicWorkerThreadFactory,
  StandardWorkerThreadFactory
}
import monix.execution.internal.{ InterceptRunnable, Platform, ScheduledExecutors }
import monix.execution.{ Cancelable, Features, Properties, Scheduler, UncaughtExceptionReporter }
// Prevents conflict with the deprecated symbol
import monix.execution.{ ExecutionModel => ExecModel }
import scala.concurrent.{ blocking, ExecutionContext, Future, Promise }
import scala.concurrent.duration.TimeUnit
import scala.util.control.NonFatal

/** An [[ExecutorScheduler]] is a class for building a
  * [[monix.execution.schedulers.SchedulerService SchedulerService]]
  * out of a Java `ExecutorService`.
  */
abstract class ExecutorScheduler(e: ExecutorService, r: UncaughtExceptionReporter)
  extends SchedulerService with ReferenceScheduler with BatchingScheduler {

  /** Returns the underlying `ExecutorService` reference. */
  def executor: ExecutorService = e

  override final protected def executeAsync(runnable: Runnable): Unit =
    e.execute(if (r eq null) runnable else InterceptRunnable(runnable, r))
  override final def reportFailure(t: Throwable): Unit =
    if (r ne null) r.reportFailure(t) else Platform.reportFailure(t)
  override final def isShutdown: Boolean =
    e.isShutdown
  override final def isTerminated: Boolean =
    e.isTerminated
  override final def shutdown(): Unit =
    e.shutdown()

  override final def awaitTermination(timeout: Long, unit: TimeUnit, awaitOn: ExecutionContext): Future[Boolean] = {
    val p = Promise[Boolean]()
    awaitOn.execute(() =>
      try blocking {
          p.success(e.awaitTermination(timeout, unit))
          ()
        }
      catch {
        case ex if NonFatal(ex) =>
          p.failure(ex); ()
      }
    )
    p.future
  }

  override def withProperties(props: Properties): SchedulerService = {
    // $COVERAGE-OFF$
    throw new NotImplementedError("ExecutorService.withExecutionModel")
    // $COVERAGE-ON$
  }

  override def withUncaughtExceptionReporter(r: UncaughtExceptionReporter): SchedulerService = {
    // $COVERAGE-OFF$
    throw new NotImplementedError("ExecutorService.withUncaughtExceptionReporter")
    // $COVERAGE-ON$
  }
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
    * @param features is the set of [[Features]] that the
    *        provided `ExecutorService` implements, see the documentation
    *        for [[monix.execution.Scheduler.features Scheduler.features]]
    */
  def apply(
    service: ExecutorService,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel,
    features: Features
  ): ExecutorScheduler = {

    // Implementations will inherit BatchingScheduler, so this is guaranteed
    val ft = features + Scheduler.BATCHING
    service match {
      case ref: ScheduledExecutorService =>
        new FromScheduledExecutor(ref, reporter, Properties[ExecModel](executionModel), ft)
      case _ =>
        val s = Defaults.scheduledExecutor
        new FromSimpleExecutor(s, service, reporter, Properties[ExecModel](executionModel), ft)
    }
  }

  /**
    * DEPRECATED — provided for binary backwards compatibility.
    *
    * Use the full-featured builder.
    */
  @deprecated("Use the full-featured builder", "3.0.0")
  def apply(
    service: ExecutorService,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel
  ): ExecutorScheduler = {
    // $COVERAGE-OFF$
    apply(service, reporter, executionModel, Features.empty)
    // $COVERAGE-ON$
  }

  /** Creates an [[ExecutorScheduler]] backed by a `ForkJoinPool`
    * that isn't integrated with Scala's `BlockContext`.
    */
  def forkJoinStatic(
    name: String,
    parallelism: Int,
    daemonic: Boolean,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel
  ): ExecutorScheduler = {

    val handler = reporter.asJava
    val pool = new AdaptedForkJoinPool(
      parallelism,
      new StandardWorkerThreadFactory(name, handler, daemonic),
      handler,
      asyncMode = true
    )

    apply(pool, reporter, executionModel, Features.empty)
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
    executionModel: ExecModel
  ): ExecutorScheduler = {

    val exceptionHandler = reporter.asJava
    val pool = new AdaptedForkJoinPool(
      parallelism,
      new DynamicWorkerThreadFactory(name, maxThreads, exceptionHandler, daemonic),
      exceptionHandler,
      asyncMode = true
    )

    apply(pool, reporter, executionModel, Features.empty)
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
    override val properties: Properties,
    override val features: Features
  ) extends ExecutorScheduler(executor, r) {

    @deprecated("Provided for backwards compatibility", "3.0.0")
    def this(
      scheduler: ScheduledExecutorService,
      executor: ExecutorService,
      r: UncaughtExceptionReporter,
      executionModel: ExecModel
    ) = {
      // $COVERAGE-OFF$
      this(scheduler, executor, r, Properties[ExecModel](executionModel), Features.empty)
      // $COVERAGE-ON$
    }

    override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable =
      ScheduledExecutors.scheduleOnce(this, scheduler)(initialDelay, unit, r)

    override def withProperties(properties: Properties): SchedulerService =
      new FromSimpleExecutor(scheduler, executor, r, properties, features)

    override def withUncaughtExceptionReporter(r: UncaughtExceptionReporter): SchedulerService =
      new FromSimpleExecutor(scheduler, executor, r, properties, features)
  }

  /** Converts a Java `ScheduledExecutorService`. */
  private final class FromScheduledExecutor(
    s: ScheduledExecutorService,
    r: UncaughtExceptionReporter,
    override val properties: Properties,
    override val features: Features
  ) extends ExecutorScheduler(s, r) {

    @deprecated("Provided for backwards compatibility", "3.0.0")
    def this(scheduler: ScheduledExecutorService, r: UncaughtExceptionReporter, executionModel: ExecModel) = {
      // $COVERAGE-OFF$
      this(scheduler, r, Properties[ExecModel](executionModel), Features.empty)
      // $COVERAGE-ON$
    }

    override def executor: ScheduledExecutorService = s

    def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      if (initialDelay <= 0) {
        execute(r)
        Cancelable.empty
      } else {
        val task = s.schedule(r, initialDelay, unit)
        Cancelable(() => { task.cancel(true); () })
      }
    }

    override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      val task = s.scheduleWithFixedDelay(r, initialDelay, delay, unit)
      Cancelable(() => { task.cancel(false); () })
    }

    override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
      val task = s.scheduleAtFixedRate(r, initialDelay, period, unit)
      Cancelable(() => { task.cancel(false); () })
    }

    override def withProperties(properties: Properties): SchedulerService =
      new FromScheduledExecutor(s, r, properties, features)

    override def withUncaughtExceptionReporter(r: UncaughtExceptionReporter): SchedulerService =
      new FromScheduledExecutor(s, r, properties, features)
  }
}
