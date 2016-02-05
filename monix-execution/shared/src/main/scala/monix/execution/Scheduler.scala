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

package monix.execution

import java.util.concurrent.TimeUnit
import monix.execution.Scheduler.FrameId
import monix.execution.cancelables.BooleanCancelable
import monix.execution.schedulers.SchedulerCompanionImpl
import org.sincron.macros.{InlineUtil, SyntaxUtil}
import org.sincron.macros.compat._
import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.language.experimental.macros

/** A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  * schedule the execution of units of work to run with a delay or periodically.
  */
@implicitNotFound(
  "Cannot find an implicit Scheduler, either " +
  "import monix.execution.Scheduler.Implicits.global or use a custom one")
abstract class Scheduler extends ExecutionContext with UncaughtExceptionReporter {
  /** Schedules the given `runnable` for immediate execution. */
  def execute(runnable: Runnable): Unit

  /** Reports that an asynchronous computation failed. */
  def reportFailure(t: Throwable): Unit

  /** Schedules a task to run in the future, after `initialDelay`.
    *
    * For example the following schedules a message to be printed to
    * standard output after 5 minutes:
    * {{{
    *   val task = scheduler.scheduleOnce(5.minutes, new Runnable {
    *     def run() = doSomething()
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
    *   val task = s.scheduleWithFixedDelay(5.seconds, 10.seconds, new Runnable {
    *     def run() = doSomething()
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
    *   val task = scheduler.scheduleAtFixedRate(5.seconds, 10.seconds , new Runnable {
    *     def run() = doSomething()
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

  /** The number of tasks that a run-loop can execute synchronously,
    * before forking into a new logical thread, or zero if batched
    * execution is not supported by this scheduler.
    *
    * The value must be greater or equal to zero and must have the
    * form `2^n - 1`. Valid values are 0, 1, 3, 7, 15 ... 511, 1023, etc.
    *
    * The number always has the form `2^n - 1` because then you
    * can do `index = (index + 1) & batchedExecutionModulus` in
    * order to find out whether the next task should execute
    * synchronously or not. This because doing a bitwise and is
    * more efficient than doing a division.
    */
  val batchedExecutionModulus: Int

  /** Executes the given callback, effectively starting a run-loop.
    *
    * Depending on the
    * [[Scheduler.batchedExecutionModulus Scheduler's batched execution]]
    * execution will happen either synchronously (current thread and call-stack) or
    * scheduled for asynchronous execution (by [[Scheduler.execute]]).
    *
    * @param cb is a callback receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    */
  final def runLoopStart(cb: FrameId => Unit): Unit =
    macro Scheduler.Macros.runLoopStartMacro

  /** Given the current `frameId`, executes the given callback.
    *
    * Depending on the [
    * [Scheduler.batchedExecutionModulus Scheduler's batched execution]]
    * settings and the given `frameId`, execution happens either synchronously
    * (current thread and call-stack) or scheduled for asynchronous execution
    * (by [[Scheduler.execute]]).
    *
    * @param frameId is a number identifying the current stack frame.
    *        Should start from zero.
    * @param cb is a callback receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    */
  def runLoopStep(frameId: FrameId)(cb: FrameId => Unit): Unit =
    macro Scheduler.Macros.runLoopStepMacro

  /** Given the current `frameId`, executes the given callback. Takes
    * a [[BooleanCancelable]] as a parameter that can be used to cancel
    * the loop.
    *
    * Depending on the
    * [[Scheduler.batchedExecutionModulus Scheduler's batched execution]]
    * settings and the given `frameId`, execution happens either synchronously
    * (current thread and call-stack) or scheduled for asynchronous execution
    * (by [[Scheduler.execute]]).
    *
    * @param frameId is a number identifying the current stack frame.
    *        Should start from zero.
    * @param cb is a callback receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    */
  def runLoopStepInterruptibly(active: BooleanCancelable, frameId: FrameId)
    (cb: FrameId => Unit): Unit =
    macro Scheduler.Macros.runLoopStepInterruptiblyMacro
}

private[monix] trait SchedulerCompanion {
  trait ImplicitsLike {
    def global: Scheduler
  }

  def Implicits: ImplicitsLike
  def global: Scheduler
}

object Scheduler extends SchedulerCompanionImpl { self: SchedulerCompanion =>
  /** An alias for a number representing an ID for the current stack frame. */
  type FrameId = Int

  object Macros {
    /** Macro for [[Scheduler.runLoopStart]] */
    def runLoopStartMacro(c: Context { type PrefixType = Scheduler })
      (cb: c.Expr[FrameId => Unit]): c.Expr[Unit] = {

      import c.universe._
      val util = SyntaxUtil[c.type](c)
      val selfExpr = c.prefix
      val nextFrameId = util.name("nextFrameId")

      val tree =
        if (util.isClean(selfExpr))
          q"""
          val $nextFrameId = 1 & $selfExpr.batchedExecutionModulus
          if ($nextFrameId > 0)
            $cb($nextFrameId)
          else
            $selfExpr.execute(new Runnable { def run(): Unit = $cb($nextFrameId) })
          """
        else {
          val scheduler = util.name("scheduler")
          q"""
          val $scheduler = $selfExpr
          val $nextFrameId = 1 & $scheduler.batchedExecutionModulus
          if ($nextFrameId > 0)
            $cb($nextFrameId)
          else
            $scheduler.execute(new Runnable { def run(): Unit = $cb($nextFrameId) })
          """
        }


      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }

    /** Macro for [[Scheduler.runLoopStep]] */
    def runLoopStepMacro(c: Context { type PrefixType = Scheduler })
      (frameId: c.Expr[FrameId])
      (cb: c.Expr[FrameId => Unit]): c.Expr[Unit] = {

      import c.universe._
      val util = SyntaxUtil[c.type](c)
      val selfExpr = c.prefix
      val nextFrameId = util.name("nextFrameId")

      val tree =
        if (util.isClean(selfExpr)) {
          q"""
          val $nextFrameId = ($frameId + 1) & $selfExpr.batchedExecutionModulus
          if ($nextFrameId > 0)
            $cb($nextFrameId)
          else
            $selfExpr.execute(new Runnable { def run(): Unit = $cb($nextFrameId) })
          """
        } else {
          val scheduler = util.name("scheduler")
          q"""
          val $scheduler = $selfExpr
          val $nextFrameId = ($frameId + 1) & $scheduler.batchedExecutionModulus
          if ($nextFrameId > 0)
            $cb($nextFrameId)
          else
            $scheduler.execute(new Runnable { def run(): Unit = $cb($nextFrameId) })
          """
        }

      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }

    /** Macro for [[Scheduler.runLoopStep]] */
    def runLoopStepInterruptiblyMacro(c: Context { type PrefixType = Scheduler })
      (active: c.Expr[BooleanCancelable], frameId: c.Expr[FrameId])
      (cb: c.Expr[FrameId => Unit]): c.Expr[Unit] = {

      import c.universe._
      val util = SyntaxUtil[c.type](c)
      val selfExpr = c.prefix
      val nextFrameId = util.name("nextFrameId")

      val tree =
        if (util.isClean(selfExpr)) {
          q"""
          val $nextFrameId = ($frameId + 1) & $selfExpr.batchedExecutionModulus
          if ($nextFrameId > 0)
            $cb($nextFrameId)
          else if (!$active.isCanceled)
            $selfExpr.execute(new Runnable { def run(): Unit = $cb($nextFrameId) })
          """
        } else {
          val scheduler = util.name("scheduler")
          q"""
          val $scheduler = $selfExpr
          val $nextFrameId = ($frameId + 1) & $scheduler.batchedExecutionModulus
          if ($nextFrameId > 0)
            $cb($nextFrameId)
          else if (!$active.isCanceled)
            $scheduler.execute(new Runnable { def run(): Unit = $cb($nextFrameId) })
          """
        }

      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }
  }
}
