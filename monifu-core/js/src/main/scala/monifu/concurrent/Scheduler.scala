/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent


import monifu.concurrent.cancelables.{MultiAssignmentCancelable, BooleanCancelable}
import monifu.concurrent.schedulers.{TrampolineScheduler, AsyncScheduler}
import monifu.concurrent.UncaughtExceptionReporter.LogExceptionsToStandardErr

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration


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
  def scheduleRecursive(initialDelay: FiniteDuration, delay: FiniteDuration, action: (() => Unit) => Unit): Cancelable = {
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
   * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def apply(reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    AsyncScheduler(reporter)
  }

  /**
   * Builds a [[monifu.concurrent.schedulers.TrampolineScheduler TrampolineScheduler]].
   *
   * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def trampoline(reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    TrampolineScheduler(reporter)
  }
}

