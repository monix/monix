/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution

import java.util.concurrent.{Executor, TimeUnit}
import monix.execution.internal.RunnableAction
import monix.execution.schedulers.SchedulerCompanionImpl
import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/** A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  * schedule the execution of units of work to run with a delay or periodically.
  */
@implicitNotFound(
  "Cannot find an implicit Scheduler, either " +
  "import monix.execution.Scheduler.Implicits.global or use a custom one")
trait Scheduler extends ExecutionContext with UncaughtExceptionReporter with Executor {
  /** Schedules the given `command` for execution at some time in the future.
    *
    * The command may execute in a new thread, in a pooled thread,
    * in the calling thread, basically at the discretion of the
    * [[Scheduler]] implementation.
    */
  def execute(command: Runnable): Unit

  /** Reports that an asynchronous computation failed. */
  def reportFailure(t: Throwable): Unit

  /** Schedules a task to run in the future, after `initialDelay`.
    *
    * For example the following schedules a message to be printed to
    * standard output after 5 minutes:
    * {{{
    *   val task = scheduler.scheduleOnce(5, TimeUnit.MINUTES, new Runnable {
    *     def run() = print("Hello, world!")
    *   })
    *
    *   // later if you change your mind ...
    *   task.cancel()
    * }}}
    *
    * @param initialDelay is the time to wait until the execution happens
    * @param unit is the time unit used for `initialDelay`
    * @param r is the callback to be executed
    * @return a `Cancelable` that can be used to cancel the created task
    *         before execution.
    */
  def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable

  /** Schedules for execution a periodic task that is first executed
    * after the given initial delay and subsequently with the given
    * delay between the termination of one execution and the
    * commencement of the next.
    *
    * For example the following schedules a message to be printed to
    * standard output every 10 seconds with an initial delay of 5
    * seconds:
    * {{{
    *   val task = s.scheduleWithFixedDelay(5, 10, TimeUnit.SECONDS, new Runnable {
    *     def run() = print("Repeated message")
    *   })
    *
    *   // later if you change your mind ...
    *   task.cancel()
    * }}}
    *
    * @param initialDelay is the time to wait until the first execution happens
    * @param delay is the time to wait between 2 successive executions of the task
    * @param unit is the time unit used for the `initialDelay` and the `delay` parameters
    * @param r is the callback to be executed
    * @return a cancelable that can be used to cancel the execution of
    *         this repeated task at any time.
    */
  def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable

  /** Schedules a periodic task that becomes enabled first after the given
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
    *   val task = scheduler.scheduleAtFixedRate(5, 10, TimeUnit.SECONDS, new Runnable {
    *     def run() = print("Repeated message")
    *   })
    *
    *   // later if you change your mind ...
    *   task.cancel()
    * }}}
    *
    * @param initialDelay is the time to wait until the first execution happens
    * @param period is the time to wait between 2 successive executions of the task
    * @param unit is the time unit used for the `initialDelay` and the `period` parameters
    * @param r is the callback to be executed
    * @return a cancelable that can be used to cancel the execution of
    *         this repeated task at any time.
    */
  def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable

  /** Returns the current time in milliseconds.  Note that while the
    * unit of time of the return value is a millisecond, the
    * granularity of the value depends on the underlying operating
    * system and may be larger.  For example, many operating systems
    * measure time in units of tens of milliseconds.
    *
    * It's the equivalent of `System.currentTimeMillis()`. When wanting
    * to measure time, do not use `System.currentTimeMillis()`
    * directly, prefer this method instead, because then it can be
    * mocked for testing purposes (see for example
    * [[monix.execution.schedulers.TestScheduler TestScheduler]])
    */
  def currentTimeMillis(): Long

  /** The [[ExecutionModel]] is a specification of how run-loops
    * and producers should behave in regards to executing tasks
    * either synchronously or asynchronously.
    */
  def executionModel: ExecutionModel

  /** Given a function that will receive the underlying
    * [[monix.execution.ExecutionModel ExecutionModel]],
    * returns a new [[Scheduler]] reference, based on the source,
    * that exposes the transformed `ExecutionModel`
    * when queried by means of the [[executionModel]] property.
    *
    * This method enables reusing global scheduler references in
    * a local scope, but with a slightly modified
    * [[monix.execution.ExecutionModel execution model]]
    * to inject.
    *
    * The contract of this method (things you can rely on):
    *
    *  1. the source `Scheduler` must not be modified in any way
    *  1. the implementation should wrap the source efficiently, such that the
    *     result mirrors the source `Scheduler` in every way except for
    *     the execution model
    *
    * Sample:
    * {{{
    *   import monix.execution.Scheduler.global
    *
    *   implicit val scheduler = {
    *     val em = global.executionModel
    *     global.withExecutionModel(em.withAutoCancelableLoops(true))
    *   }
    * }}}
    */
  def withExecutionModel(em: ExecutionModel): Scheduler
}

private[monix] trait SchedulerCompanion {
  trait ImplicitsLike {
    def global: Scheduler
    def traced: Scheduler
  }

  def Implicits: ImplicitsLike
  def global: Scheduler
  def traced: Scheduler
}

object Scheduler extends SchedulerCompanionImpl {
  self: SchedulerCompanion =>

  /** Utilities complementing the `Scheduler` interface. */
  implicit final class Extensions(val source: Scheduler) extends AnyVal with schedulers.ExecuteExtensions {
    /** Schedules a task to run in the future, after `initialDelay`.
      *
      * For example the following schedules a message to be printed to
      * standard output after 5 minutes:
      * {{{
      *   val task = scheduler.scheduleOnce(5.minutes) {
      *     print("Hello, world!")
      *   }
      *
      *   // later, if you change your mind ...
      *   task.cancel()
      * }}}
      *
      * @param initialDelay is the time to wait until the execution happens
      * @param action is the callback to be executed
      * @return a `Cancelable` that can be used to cancel the created task
      *         before execution.
      */
    def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable =
      source.scheduleOnce(initialDelay.length, initialDelay.unit, RunnableAction(action))

    /** Schedules for execution a periodic task that is first executed
      * after the given initial delay and subsequently with the given
      * delay between the termination of one execution and the
      * commencement of the next.
      *
      * For example the following schedules a message to be printed to
      * standard output every 10 seconds with an initial delay of 5
      * seconds:
      * {{{
      *   val task = s.scheduleWithFixedDelay(5.seconds, 10.seconds) {
      *     print("Repeated message")
      *   }
      *
      *   // later if you change your mind ...
      *   task.cancel()
      * }}}
      *
      * @param initialDelay is the time to wait until the first execution happens
      * @param delay is the time to wait between 2 successive executions of the task
      * @param action is the callback to be executed
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)
      (action: => Unit): Cancelable = {

      source.scheduleWithFixedDelay(initialDelay.toMillis, delay.toMillis, TimeUnit.MILLISECONDS,
        RunnableAction(action))
    }

    /** Schedules a periodic task that becomes enabled first after the given
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
      *     print("Repeated message")
      *   }
      *
      *   // later if you change your mind ...
      *   task.cancel()
      * }}}
      *
      * @param initialDelay is the time to wait until the first execution happens
      * @param period is the time to wait between 2 successive executions of the task
      * @param action is the callback to be executed
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)
      (action: => Unit): Cancelable = {

      source.scheduleAtFixedRate(initialDelay.toMillis, period.toMillis, TimeUnit.MILLISECONDS,
        RunnableAction(action))
    }
  }
}
