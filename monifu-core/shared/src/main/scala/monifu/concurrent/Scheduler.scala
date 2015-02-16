/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.concurrent

import monifu.concurrent.schedulers._

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can
 * schedule the execution of units of work to run with a delay or periodically.
 */
@implicitNotFound(
  "Cannot find an implicit Scheduler, either " +
  "import monifu.concurrent.Implicits.globalScheduler or use a custom one")
trait Scheduler extends ExecutionContext with UncaughtExceptionReporter {
  /**
   * Returns the current value of the running virtual machine's high-resolution
   * time source, in nanoseconds.
   *
   * When wanting to measure how long it took for the execution of a callback to
   * happen, do not use `System.nanoTime` directly, prefer this method instead,
   * because then it can be mocked for testing purposes (see for example
   * [[monifu.concurrent.schedulers.TestScheduler TestScheduler]])
   */
  def nanoTime(): Long

  /**
   * Schedules the given `action` for immediate execution.
   *
   * @return a [[Cancelable]] that can be used to cancel the task in case
   *         it hasn't been executed yet.
   */
  def execute(action: => Unit): Unit

  /**
   * Schedules a task to run in the future, after `initialDelay`.
   *
   * For example the following schedules a message to be printed to standard
   * output after 5 minutes:
   * {{{
   *   val task = scheduler.scheduleOnce(5.minutes) {
   *     println("Hello, world!")
   *   }
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
  def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable

  /**
   * Schedules a task to run in the future, after `initialDelay`.
   *
   * For example the following schedules a message to be printed to
   * standard output after 5 minutes:
   * {{{
   *   val task = scheduler.scheduleOnce(5.minutes, new Runnable {
   *     def run() = println("Hello, world!")
   *   })
   *
   *   // later if you change your mind ...
   *   task.cancel()
   * }}}
   *
   * @param initialDelay is the time to wait until the execution happens
   * @param r is the callback to be executed
   *
   * @return a `Cancelable` that can be used to cancel the created task
   *         before execution.
   */
  def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable

  /**
   * Schedules for execution a periodic task that is first executed
   * after the given initial delay and subsequently with the given
   * delay between the termination of one execution and the
   * commencement of the next.
   *
   * For example the following schedules a message to be printed to
   * standard output every 10 seconds with an initial delay of 5
   * seconds:
   * {{{
   *   val task = s.scheduleWithFixedDelay(5.seconds, 10.seconds, new Runnable {
   *     def run() = println("Hello, world!")
   *   })
   *
   *   // later if you change your mind ...
   *   task.cancel()
   * }}}
   *
   * @param initialDelay is the time to wait until the first execution happens
   * @param delay is the time to wait between 2 subsequent executions of the task
   * @param r is the callback to be executed
   *
   * @return a cancelable that can be used to cancel the execution of
   *         this repeated task at any time.
   */
  def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration, r: Runnable): Cancelable

  /**
   * Schedules for execution a periodic task that is first executed after the
   * given initial delay and subsequently with the given delay between the
   * termination of one execution and the commencement of the next.
   *
   * For example the following schedules a message to be printed to standard
   * output every 10 seconds with an initial delay of 5 seconds:
   * {{{
   *   val task = s.scheduleWithFixedDelay(5.seconds, 10.seconds) {
   *     println("Hello, world!")
   *   }
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
  def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(action: => Unit): Cancelable

  /**
   * Schedules a periodic task that becomes enabled first after the given
   * initial delay, and subsequently with the given period. Executions will
   * commence after `initialDelay` then `initialDelay + period`, then
   * `initialDelay + 2 * period` and so on.
   *
   * If any execution of the task encounters an exception, subsequent executions
   * are suppressed. Otherwise, the task will only terminate via cancellation or
   * termination of the scheduler. If any execution of this task takes longer
   * than its period, then subsequent executions may start late, but will not
   * concurrently execute.
   *
   * For example the following schedules a message to be printed to standard
   * output approximately every 10 seconds with an initial delay of 5 seconds:
   * {{{
   *   val task = scheduler.scheduleAtFixedRate(5.seconds, 10.seconds , new Runnable {
   *     def run() = println("Hello, world!")
   *   })
   *
   *   // later if you change your mind ...
   *   task.cancel()
   * }}}
   *
   * @param initialDelay is the time to wait until the first execution happens
   * @param period is the time to wait between 2 subsequent executions of the task
   * @param r is the callback to be executed
   *
   * @return a cancelable that can be used to cancel the execution of
   *         this repeated task at any time.
   */
  def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration, r: Runnable): Cancelable

  /**
   * Schedules a periodic task that becomes enabled first after the given
   * initial delay, and subsequently with the given period. Executions will
   * commence after `initialDelay` then `initialDelay + period`, then
   * `initialDelay + 2 * period` and so on.
   *
   * If any execution of the task encounters an exception, subsequent executions
   * are suppressed. Otherwise, the task will only terminate via cancellation or
   * termination of the scheduler. If any execution of this task takes longer
   * than its period, then subsequent executions may start late, but will not
   * concurrently execute.
   *
   * For example the following schedules a message to be printed to standard
   * output approximately every 10 seconds with an initial delay of 5 seconds:
   * {{{
   *   val task = scheduler.scheduleAtFixedRate(5.seconds, 10.seconds) {
   *     println("Hello, world!")
   *   }
   *
   *   // later if you change your mind ...
   *   task.cancel()
   * }}}
   *
   * @param initialDelay is the time to wait until the first execution happens
   * @param period is the time to wait between 2 subsequent executions of the task
   * @param action is the callback to be executed
   *
   * @return a cancelable that can be used to cancel the execution of
   *         this repeated task at any time.
   */
  def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Cancelable

  /**
   * Runs a block of code in this `ExecutionContext`.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit
}

object Scheduler extends SchedulerCompanion
