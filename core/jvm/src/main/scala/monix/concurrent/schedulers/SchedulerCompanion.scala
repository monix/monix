/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

package monix.concurrent.schedulers

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory}
import monix.concurrent.UncaughtExceptionReporter._
import asterix.atomic.Atomic
import monix.concurrent.{Scheduler, UncaughtExceptionReporter}
import scala.concurrent.ExecutionContext
import scala.concurrent.forkjoin.ForkJoinPool


private[concurrent] abstract class SchedulerCompanion {
  /**
   * [[Scheduler]] builder.
   *
   * @param executor is the `ScheduledExecutorService` that handles the scheduling
   *                 of tasks into the future.
   *
   * @param ec is the execution context in which all tasks will run.
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def apply(executor: ScheduledExecutorService, ec: ExecutionContext, r: UncaughtExceptionReporter): Scheduler = {
    AsyncScheduler(executor, ec, r)
  }

  /**
   * [[Scheduler]] builder.
   *
   * @param executor is the `ScheduledExecutorService` that handles the scheduling
   *                 of tasks into the future.
   *
   * @param ec is the execution context in which all tasks will run.
   */
  def apply(executor: ScheduledExecutorService, ec: ExecutionContext): Scheduler = {
    AsyncScheduler(executor, ec, UncaughtExceptionReporter(ec.reportFailure))
  }

  /**
   * [[Scheduler]] builder - uses Monix's default `ScheduledExecutorService` for
   * handling the scheduling of tasks.
   *
   * @param ec is the execution context in which all tasks will run.
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def apply(ec: ExecutionContext, r: UncaughtExceptionReporter): Scheduler =
    AsyncScheduler(defaultScheduledExecutor, ec, r)

  /**
   * [[Scheduler]] builder - uses Monix's default `ScheduledExecutorService` for
   * handling the scheduling of tasks.
   *
   * @param ec is the execution context in which all tasks will run.
   */
  def apply(ec: ExecutionContext): Scheduler =
    AsyncScheduler(
      defaultScheduledExecutor, ec,
      UncaughtExceptionReporter(ec.reportFailure)
    )


  /**
   * Creates a [[Scheduler]] meant for computational heavy tasks.
   *
   * Characteristics:
   *
   * - backed by Scala's `ForkJoinPool` for the task execution, in async mode
   * - uses Monix's default `ScheduledExecutorService` instance for scheduling
   * - all created threads are daemonic
   * - cooperates with Scala's `BlockContext`
   *
   * @param parallelism is the number of threads that can run in parallel
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def computation(parallelism: Int, r: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    val exceptionHandler = new UncaughtExceptionHandler {
      def uncaughtException(t: Thread, e: Throwable) =
        r.reportFailure(e)
    }

    val pool = new scala.concurrent.forkjoin.ForkJoinPool(
      parallelism,
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      exceptionHandler,
      true // asyncMode
    )

    val context = ExecutionContext.fromExecutor(pool, r.reportFailure)
    AsyncScheduler(defaultScheduledExecutor, context, r)
  }

  /**
   * Creates a [[Scheduler]] meant for blocking I/O tasks.
   *
   * Characteristics:
   *
   * - backed by a cached `ThreadPool` executor with 60 seconds of keep-alive
   * - the maximum number of threads is unbounded, as recommended for blocking I/O
   * - uses Monix's default `ScheduledExecutorService` instance for scheduling
   * - doesn't cooperate with Scala's `BlockContext` only because it is unbounded
   *
   * @param name the created threads name prefix, for easy identification.
   *
   * @param daemonic specifies whether the created threads should be daemonic
   *                 (non-daemonic threads are blocking the JVM process on exit).
   *
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def io(name: String = "monix-io", daemonic: Boolean = true,
    r: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    val threadFactory = new ThreadFactory {
      private[this] val counter = Atomic(0L)
      def newThread(r: Runnable): Thread = {
        val th = new Thread(r)
        th.setDaemon(daemonic)
        th.setName(name + "-" + counter.getAndIncrement().toString)
        th
      }
    }

    val context = ExecutionContext.fromExecutor(
      Executors.newCachedThreadPool(threadFactory),
      r.reportFailure
    )

    AsyncScheduler(defaultScheduledExecutor, context, r)
  }

  /**
   * Builds a [[Scheduler]] that schedules and executes tasks on its own thread.
   *
   * Characteristics:
   *
   * - backed by a single-threaded `ScheduledExecutorService` that takes care
   *   of both scheduling tasks in the future and of executing tasks
   * - does not cooperate with Scala's `BlockingContext`, so tasks should not
   *   block on the result of other tasks scheduled to run on this same thread
   *
   * @param name is the name of the created thread, for easy identification
   *
   * @param daemonic specifies whether the created thread should be daemonic
   *                 (non-daemonic threads are blocking the JVM process on exit)
   *
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def singleThread(name: String, daemonic: Boolean = true,
    r: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    val executor =
      Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
        def newThread(r: Runnable) = {
          val th = new Thread(r)
          th.setName(name)
          th.setDaemon(daemonic)
          th
        }
      })

    val context = new ExecutionContext {
      def reportFailure(t: Throwable) = r.reportFailure(t)
      def execute(runnable: Runnable) = executor.execute(runnable)
    }

    AsyncScheduler(executor, context, r)
  }

  /**
   * The default `ScheduledExecutor` instance.
   */
  private[concurrent] lazy val defaultScheduledExecutor =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val th = new Thread(r)
        th.setDaemon(true)
        th.setName("monix-scheduler")
        th
      }
    })
}
