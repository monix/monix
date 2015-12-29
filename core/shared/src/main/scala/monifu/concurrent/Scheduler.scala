/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

import java.util.concurrent.TimeUnit

import monifu.concurrent.Scheduler.Environment
import monifu.concurrent.schedulers.SchedulerCompanion
import monifu.internal.concurrent.RunnableAction
import monifu.internal.math
import math.roundToPowerOf2
import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  * schedule the execution of units of work to run with a delay or periodically.
  */
@implicitNotFound(
  "Cannot find an implicit Scheduler, either " +
  "import monifu.concurrent.Implicits.globalScheduler or use a custom one")
trait Scheduler extends ExecutionContext with UncaughtExceptionReporter {
  /** Schedules the given `runnable` for immediate execution. */
  def execute(runnable: Runnable): Unit

  /** Reports that an asynchronous computation failed. */
  def reportFailure(t: Throwable): Unit

  /** Schedules the given `runnable` for immediate execution.
    *
    * @return a [[Cancelable]] that can be used to cancel the scheduled task
    *         in case it hasn't been executed yet.
    */
  def scheduleOnce(r: Runnable): Cancelable

  /** Schedules a task to run in the future, after `initialDelay`.
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
    * @param unit is the time unit used for `initialDelay`
    * @param r is the callback to be executed
    *
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
    *   val task = s.scheduleWithFixedDelay(5.seconds, 10.seconds, new Runnable {
    *     def run() = println("Hello, world!")
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
    *
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
    *   val task = scheduler.scheduleAtFixedRate(5.seconds, 10.seconds , new Runnable {
    *     def run() = println("Hello, world!")
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
    *
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
    * [[monifu.concurrent.schedulers.TestScheduler TestScheduler]])
    */
  def currentTimeMillis(): Long

  /** Information about the environment on top of
    * which our scheduler runs on.
    */
  def env: Environment
}

object Scheduler extends SchedulerCompanion {
  implicit class Extensions(val scheduler: Scheduler) extends AnyVal {
    /** Schedules the given `action` for immediate execution. */
    def execute(action: => Unit): Unit =
      scheduler.execute(RunnableAction(action))

    /** Schedules a task to run in the future, after `initialDelay`.
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
    def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable =
      scheduler.scheduleOnce(initialDelay.length, initialDelay.unit, RunnableAction(action))


    /** Schedules a task to run in the future, after `initialDelay`.
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
      * @param unit is the time unit used for `initialDelay`
      * @param action is the callback to be executed
      *
      * @return a `Cancelable` that can be used to cancel the created task
      *         before execution.
      */
    def scheduleOnce(initialDelay: Long, unit: TimeUnit)(action: => Unit): Cancelable =
      scheduler.scheduleOnce(initialDelay, unit, RunnableAction(action))

    /** Schedules a task to run in the future, after `initialDelay`.
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
    def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable =
      scheduler.scheduleOnce(initialDelay.length, initialDelay.unit, r)

    /** Schedules for execution a periodic task that is first executed
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
      * @param delay is the time to wait between 2 successive executions of the task
      * @param r is the callback to be executed
      *
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration, r: Runnable): Cancelable = {
      if (initialDelay.unit == delay.unit)
        scheduler.scheduleWithFixedDelay(initialDelay.length, delay.length, initialDelay.unit, r)
      else {
        val initialDelayMs = initialDelay.toMillis
        val delayMs = delay.toMillis
        scheduler.scheduleWithFixedDelay(initialDelayMs, delayMs, TimeUnit.MILLISECONDS, r)
      }
    }

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
      * @param delay is the time to wait between 2 successive executions of the task
      * @param action is the callback to be executed
      *
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(action: => Unit): Cancelable = {
      val r = RunnableAction(action)
      scheduler.scheduleWithFixedDelay(initialDelay, delay, r)
    }


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
      * @param delay is the time to wait between 2 successive executions of the task
      * @param unit is the time unit used for the `initialDelay` and the `delay` parameters
      * @param action is the callback to be executed
      *
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit)(action: => Unit): Cancelable =
      scheduler.scheduleWithFixedDelay(initialDelay, delay, unit, RunnableAction(action))

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
      *   val task = scheduler.scheduleAtFixedRate(5.seconds, 10.seconds , new Runnable {
      *     def run() = println("Hello, world!")
      *   })
      *
      *   // later if you change your mind ...
      *   task.cancel()
      * }}}
      *
      * @param initialDelay is the time to wait until the first execution happens
      * @param period is the time to wait between 2 successive executions of the task
      * @param r is the callback to be executed
      *
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration, r: Runnable): Cancelable = {
      if (initialDelay.unit == period.unit)
        scheduler.scheduleAtFixedRate(initialDelay.length, period.length, initialDelay.unit, r)
      else {
        val initialDelayMs = initialDelay.toMillis
        val periodMs = period.toMillis
        scheduler.scheduleAtFixedRate(initialDelayMs, periodMs, TimeUnit.MILLISECONDS, r)
      }
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
      *     println("Hello, world!")
      *   }
      *
      *   // later if you change your mind ...
      *   task.cancel()
      * }}}
      *
      * @param initialDelay is the time to wait until the first execution happens
      * @param period is the time to wait between 2 successive executions of the task
      * @param action is the callback to be executed
      *
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Cancelable =
      scheduler.scheduleAtFixedRate(initialDelay, period, RunnableAction(action))

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
      *     println("Hello, world!")
      *   }
      *
      *   // later if you change your mind ...
      *   task.cancel()
      * }}}
      *
      * @param initialDelay is the time to wait until the first execution happens
      * @param period is the time to wait between 2 successive executions of the task
      * @param unit is the time unit used for the `initialDelay` and the `period` parameters
      * @param action is the callback to be executed
      *
      * @return a cancelable that can be used to cancel the execution of
      *         this repeated task at any time.
      */
    def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit)(action: => Unit): Cancelable =
      scheduler.scheduleAtFixedRate(initialDelay, period, unit, RunnableAction(action))
  }

  /**
   * Information about the environment on top of which
   * our scheduler runs on.
   */
  final class Environment private
    (_batchSize: Int, _platform: Platform.Value) {

    /**
     * Recommended batch size used for breaking synchronous loops in
     * asynchronous batches. When streaming value from a producer to
     * a synchronous consumer it's recommended to break the streaming
     * in batches as to not hold the current thread or run-loop
     * indefinitely.
     *
     * Working with power of 2, because then for applying the modulo
     * operation we can just do:
     * {{{
     *   val modulus = scheduler.env.batchSize - 1
     *   // ...
     *   nr = (nr + 1) & modulus
     * }}}
     */
    val batchSize: Int =
      roundToPowerOf2(_batchSize)

    /**
     * Represents the platform our scheduler runs on.
     */
    val platform: Platform.Value =
      _platform

    override def equals(other: Any): Boolean = other match {
      case that: Environment =>
        batchSize == that.batchSize &&
        platform == that.platform
      case _ => false
    }

    override val hashCode: Int = {
      val state = Seq(batchSize.hashCode(), platform.hashCode())
      state.foldLeft(0)((a, b) => 31 * a + b)
    }
  }

  object Environment {
    /** Builder for [[Environment]] */
    def apply(batchSize: Int, platform: Platform.Value): Environment = {
      new Environment(batchSize, platform)
    }
  }

  /**
   * Represents the platform our scheduler runs on.
   */
  object Platform extends Enumeration {
    val JVM = Value("JVM")
    val JS = Value("JS")
    val Fake = Value("Fake")
  }
}
