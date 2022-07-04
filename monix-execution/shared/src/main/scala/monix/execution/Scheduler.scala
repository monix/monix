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

package monix.execution

import java.util.concurrent.Executor
import monix.execution.internal.RunnableAction
import monix.execution.schedulers.SchedulerCompanionImpl
import monix.execution.internal.TypeInfoExtractor

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS, TimeUnit }

/** A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  * schedule the execution of units of work to run with a delay or periodically.
  */
@implicitNotFound(
  "Cannot find an implicit Scheduler, either " +
    "import monix.execution.Scheduler.Implicits.global or use a custom one"
)
trait Scheduler extends ExecutionContext with UncaughtExceptionReporter with Executor {
  /** Schedules the given `command` for execution at some time in the future.
    *
    * The command may execute in a new thread, in a pooled thread,
    * in the calling thread, basically at the discretion of the
    * [[Scheduler]] implementation.
    */
  def execute(command: Runnable): Unit

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

  /** Returns the current time, as a Unix timestamp (number of time units
    * since the Unix epoch).
    *
    * This is the equivalent of Java's `System.currentTimeMillis`,
    * or of `CLOCK_REALTIME` from Linux's `clock_gettime()`.
    *
    * The provided `TimeUnit` determines the time unit of the output,
    * its precision, but not necessarily its resolution, which is
    * implementation dependent. For example this will return the number
    * of milliseconds since the epoch:
    *
    * {{{
    *   import scala.concurrent.duration.MILLISECONDS
    *
    *   scheduler.clockRealTime(MILLISECONDS)
    * }}}
    *
    * N.B. the resolution is limited by the underlying implementation
    * and by the underlying CPU and OS. If the implementation uses
    * `System.currentTimeMillis`, then it can't have a better
    * resolution than 1 millisecond, plus depending on underlying
    * runtime (e.g. Node.js) it might return multiples of 10
    * milliseconds or more.
    *
    * See [[clockMonotonic]], for fetching a monotonic value that
    * may be better suited for doing time measurements.
    */
  def clockRealTime(unit: TimeUnit): Long

  /**
    * Returns a monotonic clock measurement, if supported by the
    * underlying platform.
    *
    * This is the pure equivalent of Java's `System.nanoTime`,
    * or of `CLOCK_MONOTONIC` from Linux's `clock_gettime()`.
    *
    * {{{
    *   timer.clockMonotonic(NANOSECONDS)
    * }}}
    *
    * The returned value can have nanoseconds resolution and represents
    * the number of time units elapsed since some fixed but arbitrary
    * origin time. Usually this is the Unix epoch, but that's not
    * a guarantee, as due to the limits of `Long` this will overflow in
    * the future (2^63^ is about 292 years in nanoseconds) and the
    * implementation reserves the right to change the origin.
    *
    * The return value should not be considered related to wall-clock
    * time, the primary use-case being to take time measurements and
    * compute differences between such values, for example in order to
    * measure the time it took to execute a task.
    *
    * As a matter of implementation detail, Monix's `Scheduler`
    * implementations use `System.nanoTime` and the JVM will use
    * `CLOCK_MONOTONIC` when available, instead of `CLOCK_REALTIME`
    * (see `clock_gettime()` on Linux) and it is up to the underlying
    * platform to implement it correctly.
    *
    * And be warned, there are platforms that don't have a correct
    * implementation of `CLOCK_MONOTONIC`. For example at the moment of
    * writing there is no standard way for such a clock on top of
    * JavaScript and the situation isn't so clear cut for the JVM
    * either, see:
    *
    *  - [[https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6458294 bug report]]
    *  - [[http://cs.oswego.edu/pipermail/concurrency-interest/2012-January/008793.html concurrency-interest]]
    *    discussion on the X86 tsc register
    *
    * The JVM tries to do the right thing and at worst the resolution
    * and behavior will be that of `System.currentTimeMillis`.
    *
    * The recommendation is to use this monotonic clock when doing
    * measurements of execution time, or if you value monotonically
    * increasing values more than a correspondence to wall-time, or
    * otherwise prefer [[clockRealTime]].
    */
  def clockMonotonic(unit: TimeUnit): Long

  /** Reports that an asynchronous computation failed. */
  def reportFailure(t: Throwable): Unit

  /**
    * Returns a new `Scheduler` that piggybacks on this, but uses
    * the given exception reporter for reporting uncaught errors.
    *
    * Sample:
    * {{{
    *   import monix.execution.Scheduler.global
    *   import monix.execution.UncaughtExceptionReporter
    *
    *   implicit val scheduler = {
    *     val r = UncaughtExceptionReporter { e =>
    *       e.printStackTrace()
    *     }
    *     global.withUncaughtExceptionReporter(r)
    *   }
    * }}}
    */
  def withUncaughtExceptionReporter(r: UncaughtExceptionReporter): Scheduler

  /**
    * Exposes a set of flags that describes the [[Scheduler]]'s features.
    */
  def features: Features = {
    // $COVERAGE-OFF$
    Features.empty
    // $COVERAGE-ON$
  }

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
  final def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable =
    scheduleOnce(initialDelay.length, initialDelay.unit, RunnableAction(action))

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
  final def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(action: => Unit): Cancelable =
    scheduleWithFixedDelay(initialDelay.toMillis, delay.toMillis, MILLISECONDS, RunnableAction(action))

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
  final def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Cancelable =
    scheduleAtFixedRate(initialDelay.toMillis, period.toMillis, MILLISECONDS, RunnableAction(action))

  /** Schedules the given callback for asynchronous
    * execution in the thread-pool, but also indicates the
    * start of a
    * [[monix.execution.schedulers.TrampolinedRunnable thread-local trampoline]]
    * in case the scheduler is a
    * [[monix.execution.schedulers.BatchingScheduler BatchingScheduler]].
    *
    * This utility is provided as an optimization. If you don't understand
    * what this does, then don't worry about it.
    *
    * @param cb the callback to execute asynchronously
    */
  final def executeAsyncBatch(cb: schedulers.TrampolinedRunnable): Unit = {
    val r = schedulers.StartAsyncBatchRunnable(cb, this)
    execute(r)
  }

  /** Schedules the given callback for immediate execution as a
    * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]].
    * Depending on the execution context, it might
    * get executed on the current thread by using an internal
    * trampoline, so it is still safe from stack-overflow exceptions.
    *
    * @param cb the callback to execute asynchronously
    */
  final def executeTrampolined(cb: schedulers.TrampolinedRunnable): Unit =
    execute(cb)

  /** [[Properties]] is the "environment" exposed by the Scheduler.
    * 
    * Can be used for dependency-injection, like configuration options that can influence the runtime-behavior.
    * For example: ExecutionModel
    * {{{
    *   import monix.execution.Scheduler.global
    *   val executionModel = global.properties.getWithDefault[ExecutionModel](ExecutionModel.Default)
    * }}}
    */
  def properties: Properties

  /** [[Properties]] is the "environment" exposed by the Scheduler.
    *
    * Returns a new [[Scheduler]] reference, based on the source,
    * that exposes the transformed environment
    * when queried by means of its [[properties]].
    *
    * This method enables reusing global scheduler references in
    * a local scope, but with modified [[Properties environment]].
    *
    * The contract of this method (things you can rely on):
    *
    *  1. the source `Scheduler` must not be modified in any way
    *  1. the implementation should wrap the source efficiently, such that the
    *     result mirrors the source `Scheduler` in every way except for
    *     the [[Properties environment]] model
    *
    * {{{
    *   import monix.execution.Scheduler.global
    *   implicit val scheduler = global.withProperties(Properties[ExecutionModel](SynchronousExecution))
    * }}}
    */
  def withProperties(properties: Properties): Scheduler

  /** [[Properties]] is the "environment" exposed by the Scheduler.
    *
    * Returns a new [[Scheduler]] reference, based on the source,
    * with a single modification to the environment.
    *
    * {{{
    *   import monix.execution.Scheduler.global
    *   implicit val scheduler = global.withProperty[ExecutionModel](SynchronousExecution)
    * }}}
    */
  def withProperty[A]: Scheduler.ApplyBuilder1[A] = new Scheduler.ApplyBuilder1[A](this)
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

  /** The [[Scheduler]] supports processing in batches via an internal
    * trampoline.
    *
    * Schedulers that implement the batching behavior will recognize
    * [[monix.execution.schedulers.TrampolinedRunnable]] instances
    * (via `instanceOf` checks) and make an effort to execute them on
    * the current thread.
    *
    * This flag is exposed via [[Scheduler.features]].
    *
    * @see [[monix.execution.schedulers.BatchingScheduler BatchingScheduler]]
    *      for an implementation.
    */
  val BATCHING = Features.flag(1)

  /** Flag signaling that the [[Scheduler]] implementation can transport
    * [[monix.execution.misc.Local Local]] variables over async boundaries.
    *
    * @see [[monix.execution.schedulers.TracingScheduler TracingScheduler]] and
    *      [[monix.execution.schedulers.TracingSchedulerService TracingSchedulerService]]
    *      for implementations.
    */
  val TRACING = Features.flag(2)

  /** Utilities complementing the `Scheduler` interface. */
  implicit final class Extensions(val source: Scheduler) extends AnyVal with schedulers.ExecuteExtensions {

    @deprecated("Extension methods are now implemented on `Scheduler` directly", "3.4.0")
    def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable =
      source.scheduleOnce(initialDelay.length, initialDelay.unit, RunnableAction(action))

    @deprecated("Extension methods are now implemented on `Scheduler` directly", "3.4.0")
    def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(action: => Unit): Cancelable = {
      source.scheduleWithFixedDelay(initialDelay.toMillis, delay.toMillis, MILLISECONDS, RunnableAction(action))
    }

    @deprecated("Extension methods are now implemented on `Scheduler` directly", "3.4.0")
    def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Cancelable = {
      source.scheduleAtFixedRate(initialDelay.toMillis, period.toMillis, MILLISECONDS, RunnableAction(action))
    }

    /** DEPRECATED — use [[Scheduler.clockRealTime clockRealTime(MILLISECONDS)]]. */
    @deprecated("Use clockRealTime(MILLISECONDS)", "3.0.0")
    def currentTimeMillis(): Long = {
      // $COVERAGE-OFF$
      source.clockRealTime(MILLISECONDS)
      // $COVERAGE-ON$
    }
  }

  class ApplyBuilder1[A](val scheduler: Scheduler) {}

  implicit class ApplyBuilder1Ext[A](applyBuilder1: ApplyBuilder1[A])(implicit t: TypeInfoExtractor[A]) {
    def apply(value: A): Scheduler =
      applyBuilder1.scheduler.withProperties(applyBuilder1.scheduler.properties.withProperty[A](value))
  }

}
