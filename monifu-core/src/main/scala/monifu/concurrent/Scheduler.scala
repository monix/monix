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

/**
 * A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can schedule
 * the execution of units of work to run with a delay or periodically.
 */
@implicitNotFound("Cannot find an implicit Scheduler, either import monifu.concurrent.Scheduler.Implicits.global or use a custom one")
trait Scheduler extends ExecutionContext {
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
   * Declares easy to use implicit [[Scheduler]] instances.
   *
   * Example:
   * {{{
   *   import monifu.concurrent.Scheduler.Implicits.global
   * }}}
   */
  object Implicits {
    /**
     * A global [[Scheduler]] instance, provided for convenience, piggy-backing
     * on top of Scala's own `concurrent.ExecutionContext.global`, which is a
     * `ForkJoinPool`.
     *
     * It can be tuned by setting the following JVM system properties:
     *
     * - "scala.concurrent.context.minThreads" an integer specifying the minimum
     *   number of active threads in the pool
     *
     * - "scala.concurrent.context.maxThreads" an integer specifying the maximum
     *   number of active threads in the pool
     *
     * - "scala.concurrent.context.numThreads" can be either an integer,
     *   specifying the parallelism directly or a string with the format "xNUM"
     *   (e.g. "x1.5") specifying the multiplication factor of the number of
     *   available processors (taken with `Runtime.availableProcessors`)
     *
     * The formula for calculating the parallelism in our pool is
     * `min(maxThreads, max(minThreads, numThreads))`.
     *
     * To set a system property from the command line, a JVM parameter must be
     * given to the `java` command as `-Dname=value`. So as an example, to customize
     * this global scheduler, we could start our process like this:
     *
     * <pre>
     *   java -Dscala.concurrent.context.minThreads=2 \
     *        -Dscala.concurrent.context.maxThreads=30 \
     *        -Dscala.concurrent.context.numThreads=x1.5 \
     *        ...
     * </pre>
     *
     * As a note, this being backed by Scala's own global execution context,
     * it is cooperating with Scala's BlockContext, so when operations marked
     * with `scala.concurrent.blocking` are encountered, the thread-pool may
     * decide to add extra threads in the pool. However this is not a thread-pool
     * that is optimal for doing blocking operations, so for example if you want
     * to do a lot of blocking I/O, then use a Scheduler backed by a
     * thread-pool that is more optimal for blocking. See for example
     * [[Scheduler.io]].
     */
    implicit lazy val global: Scheduler =
      AsyncScheduler(defaultScheduledExecutor, ExecutionContext.Implicits.global)
  }

  /**
   * [[Scheduler]] builder.
   *
   * @param executor is the `ScheduledExecutorService` that handles the scheduling
   *                 of tasks into the future.
   * @param ec is the execution context in which all tasks will run.
   */
  def apply(executor: ScheduledExecutorService, ec: ExecutionContext): Scheduler =
    AsyncScheduler(executor, ec)

  /**
   * [[Scheduler]] builder - uses Monifu's default `ScheduledExecutorService` for
   * handling the scheduling of tasks.
   *
   * @param ec is the execution context in which all tasks will run.
   */
  def apply(ec: ExecutionContext): Scheduler =
    AsyncScheduler(defaultScheduledExecutor, ec)

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
   * @param reporter the callback that is supposed to log uncaught exceptions
   */
  def computation(parallelism: Int, reporter: Throwable => Unit = defaultReporter): Scheduler = {
    val exceptionHandler = new UncaughtExceptionHandler {
      def uncaughtException(t: Thread, e: Throwable) =
        reporter(e)
    }

    val pool = new scala.concurrent.forkjoin.ForkJoinPool(
      parallelism,
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      exceptionHandler,
      true // asyncMode
    )

    val context = ExecutionContext.fromExecutor(pool, reporter)
    AsyncScheduler(defaultScheduledExecutor, context)
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
   * @param name the created threads name prefix, for easy identification
   * @param daemonic specifies whether the created threads should be daemonic
   *                 (non-daemonic threads are blocking the JVM process on exit)
   */
  def io(name: String = "monifu-io", daemonic: Boolean = true): Scheduler = {
    val context = ExecutionContext.fromExecutor(
      Executors.newCachedThreadPool(new ThreadFactory {
        private[this] val counter = Atomic(0L)
        def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(daemonic)
          th.setName(name + "-" + counter.getAndIncrement().toString)
          th
        }
      }))

    AsyncScheduler(defaultScheduledExecutor, context)
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
   * @param reporter is the callback that logs uncaught exceptions
   * @param daemonic specifies whether the created thread should be daemonic
   *                 (non-daemonic threads are blocking the JVM process on exit)
   */
  def singleThread(name: String, reporter: Throwable => Unit = Scheduler.defaultReporter, daemonic: Boolean = true): Scheduler = {
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
      def reportFailure(t: Throwable) = reporter(t)
      def execute(runnable: Runnable) = executor.execute(runnable)
    }
    
    AsyncScheduler(executor, context)
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

  def defaultReporter(ex: Throwable): Unit = {
    ExecutionContext.defaultReporter(ex)
  }
}

