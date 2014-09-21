/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.concurrent

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory}

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.{BooleanCancelable, MultiAssignmentCancelable}
import monifu.concurrent.schedulers._

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ForkJoinPool
import UncaughtExceptionReporter.LogExceptionsToStandardErr

/**
 * A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can schedule
 * the execution of units of work to run with a delay or periodically.
 */
@implicitNotFound(
  "Cannot find an implicit Scheduler, either " +
  "import monifu.concurrent.Implicits.globalScheduler or use a custom one")
trait Scheduler extends ExecutionContext with UncaughtExceptionReporter {
  /**
   * Schedules the given `action` for immediate execution.
   *
   * @return a [[Cancelable]] that can be used to cancel the task in case
   *         it hasn't been executed yet.
   */
  def scheduleOnce(action: => Unit): Cancelable = {
    val sub = BooleanCancelable()
    execute(new Runnable {
      def run(): Unit =
        if (!sub.isCanceled)
          action
    })
    sub
  }

  /**
   * Schedules a task to run in the future, after `initialDelay`.
   *
   * For example the following schedules a message to be printed to
   * standard output after 5 minutes:
   * {{{
   *   val task = scheduler.scheduleOnce(5.minutes, {
   *     println("Hello, world!")
   *   })
   *
   *   // later if you change your mind ...
   *   task.cancel()
   * }}}
   *
   * @param initialDelay is the time to wait until the execution happens
   * @param action is the callback to be executed
   *
   * @return a `Cancelable` that can be used to cancel the created task
   *         before execution.
   */
  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable

  /**
   * Schedules a task for execution repeated with the given `delay` between
   * executions. The first execution happens after `initialDelay`.
   *
   * For example the following schedules a message to be printed to
   * standard output every 10 seconds with an initial delay of 5 seconds:
   * {{{
   *   val task = scheduler.scheduleRepeated(5.seconds, 10.seconds , {
   *     println("Hello, world!")
   *   })
   *
   *   // later if you change your mind ...
   *   task.cancel()
   * }}}
   *
   * @param initialDelay is the time to wait until the first execution happens
   * @param delay is the time to wait between 2 subsequent executions of the task
   * @param action is the callback to be executed
   *
   * @return a cancelable that can be used to cancel the execution of
   *         this repeated task at any time.
   */
  def scheduleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration, action: => Unit): Cancelable = {
    scheduleRecursive(initialDelay, delay, { reschedule =>
      action
      reschedule()
    })
  }

  /**
   * Schedules a task for periodic execution. The callback scheduled for
   * execution receives a function as parameter, a function that can be used
   * to reschedule the execution or consequently to stop it.
   *
   * This variant of [[Scheduler.scheduleRepeated scheduleRepeated]] is
   * useful if at some point you want to stop the execution from within the
   * callback.
   *
   * The following increments a number every 5 seconds until the number reaches
   * 20, then prints a message and stops:
   * {{{
   *   var counter = 0
   *
   *   scheduler.schedule(5.seconds, 5.seconds, { reschedule =>
   *     count += 1
   *     if (counter < 20)
   *       reschedule()
   *     else
   *       println("Done!")
   *   })
   * }}}
   *
   * @param initialDelay is the time to wait until the first execution happens
   * @param delay is the time to wait between 2 subsequent executions of the task
   * @param action is the callback to be executed
   *
   * @return a cancelable that can be used to cancel the task at any time.
   */
  def scheduleRecursive(initialDelay: FiniteDuration, delay: FiniteDuration,
      action: (() => Unit) => Unit): Cancelable = {

    val sub = MultiAssignmentCancelable()
    def reschedule(): Unit =
      sub() = scheduleOnce(delay, action(reschedule))

    sub() = scheduleOnce(initialDelay, action(reschedule))
    sub
  }

  /**
   * Runs a block of code in this `ExecutionContext`.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit
}

object Scheduler {
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
   * [[Scheduler]] builder - uses Monifu's default `ScheduledExecutorService` for
   * handling the scheduling of tasks.
   *
   * @param ec is the execution context in which all tasks will run.
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def apply(ec: ExecutionContext, r: UncaughtExceptionReporter): Scheduler =
    AsyncScheduler(defaultScheduledExecutor, ec, r)

  /**
   * [[Scheduler]] builder - uses Monifu's default `ScheduledExecutorService` for
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
   * - uses Monifu's default `ScheduledExecutorService` instance for scheduling
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
   * - uses Monifu's default `ScheduledExecutorService` instance for scheduling
   * - doesn't cooperate with Scala's `BlockContext` only because it is unbounded
   *
   * @param name the created threads name prefix, for easy identification.
   *
   * @param daemonic specifies whether the created threads should be daemonic
   *                 (non-daemonic threads are blocking the JVM process on exit).
   *
   * @param r is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def io(name: String = "monifu-io", daemonic: Boolean = true,
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
        th.setName("monifu-scheduler")
        th
      }
    })
}

