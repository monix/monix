package monix.execution

import monix.execution.cancelables.BooleanCancelable
import org.sincron.macros.{InlineUtil, SyntaxUtil}
import org.sincron.macros.compat._
import scala.language.experimental.macros

object RunLoop {
  /** An alias for a number representing an ID for the current stack frame. */
  type FrameId = Int

  /** Returns `true` if the run-loop implied by the given
    * [[Scheduler]] is always doing asynchronous execution or not.
    *
    * Note that when `isAlwaysAsync` is `true`, that means that
    * [[RunLoop.start]], [[RunLoop.step]].
    *
    * @param s is the [[Scheduler]] that drives our run-loop.
    */
  def isAlwaysAsync(implicit s: Scheduler): Boolean =
    macro Macros.isAlwaysAsync

  /** Executes the given callback, effectively starting a run-loop.
    *
    * Depending on [[Scheduler.batchedExecutionModulus]],
    * execution will happen either synchronously (current thread and call-stack) or
    * scheduled for asynchronous execution (by [[Scheduler.execute]]).
    * To find out what will happen before calling `start`, you can
    * use the [[RunLoop.isAlwaysAsync]] helper.
    *
    * @param runnable is an expression receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    * @param s is the [[Scheduler]] that drives our run-loop.
    */
  def start(runnable: FrameId => Unit)(implicit s: Scheduler): Unit =
    macro Macros.start

  /** Executes the given callback, forcing an asynchronous boundary and
    * starting a run-loop.
    *
    * Compared with [[RunLoop.start]] this macro always executes the given
    * runnable asynchronously.
    *
    * @param runnable is an expression receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    * @param s is the [[Scheduler]] that drives our run-loop.
    */
  def startAsync(runnable: FrameId => Unit)(implicit s: Scheduler): Unit =
    macro Macros.startAsync

  /** Executes the given callback immediately, irregardless of the
    * Scheduler configuration.
    *
    * Compared with [[RunLoop.start]] this macro always executes the given
    * runnable immediately.
    *
    * @param runnable is an expression to execute, receiving the next
    *        `FrameId` at the time of execution, to be used in the
    *        next `runLoop` invocation.
    *
    * @param s is the [[Scheduler]] that drives our run-loop.
    */
  def startNow(runnable: FrameId => Unit)(implicit s: Scheduler): Unit =
    macro Macros.startNow

  /** Given the current `frameId`, executes the given callback.
    *
    * Depending on [[Scheduler.batchedExecutionModulus]],
    * execution will happen either synchronously (current thread and call-stack) or
    * scheduled for asynchronous execution (by [[Scheduler.execute]]).
    *
    * @param frameId is a number identifying the current stack frame.
    *        Should start from zero.
    *
    * @param runnable is an expression receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    *
    * @param s is the [[Scheduler]] that drives our run-loop.
    */
  def step(frameId: FrameId)(runnable: FrameId => Unit)(implicit s: Scheduler): Unit =
    macro Macros.step

  /** Given the current `frameId`, executes the given callback. Takes
    * a [[BooleanCancelable]] as a parameter that can be used to cancel
    * the loop.
    *
    * Depending on [[Scheduler.batchedExecutionModulus]],
    * execution will happen either synchronously (current thread and call-stack) or
    * scheduled for asynchronous execution (by [[Scheduler.execute]]).
    *
    * @param active is a cancelable that can be used to check if the
    *        run-loop is canceled and thus avoid to trigger the next step.
    *        Note that checking `active.isCanceled` is weak and only happens
    *        on asynchronous boundaries for performance reasons (in other words
    *        don't count on cancellation to be immediate).
    *
    * @param frameId is a number identifying the current stack frame.
    *        Should start from zero.
    *
    * @param runnable is a callback receiving the next `FrameId` at the time
    *        of execution, to be used in the next `runLoop` invocation.
    */
  def stepInterruptibly(active: BooleanCancelable, frameId: FrameId)
    (runnable: FrameId => Unit)(implicit s: Scheduler): Unit =
    macro Macros.stepInterruptibly

  /** Macro implementations for [[RunLoop]]. */
  object Macros {
    /** Macro implementation for [[RunLoop.isAlwaysAsync]]. */
    def isAlwaysAsync(c: Context)(s: c.Expr[Scheduler]): c.Expr[Boolean] = {
      import c.universe._
      reify(s.splice.batchedExecutionModulus == 0)
    }

    /** Macro for [[RunLoop.start]] */
    def start(c: Context)(runnable: c.Expr[FrameId => Unit])(s: c.Expr[Scheduler]): c.Expr[Unit] = {
      import c.universe._
      val util = SyntaxUtil[c.type](c)
      val nextFrameId = util.name("nextFrameId")

      val tree =
        if (util.isClean(s, runnable))
          q"""
          val $nextFrameId = 1 & $s.batchedExecutionModulus
          if ($nextFrameId > 0)
            $runnable($nextFrameId)
          else
            $s.execute(new Runnable { def run(): Unit = $runnable($nextFrameId) })
          """
        else {
          val ec = util.name("s")
          val fn = util.name("t")
          q"""
          val $ec = $s
          val $fn = $runnable
          val $nextFrameId = 1 & $ec.batchedExecutionModulus
          if ($nextFrameId > 0)
            $fn($nextFrameId)
          else
            $ec.execute(new Runnable { def run(): Unit = $fn(0) })
          """
        }


      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }

    /** Macro for [[RunLoop.startAsync]] */
    def startAsync(c: Context)(runnable: c.Expr[FrameId => Unit])(s: c.Expr[Scheduler]): c.Expr[Unit] = {
      import c.universe._
      val tree = q"""$s.execute(new Runnable { def run(): Unit = $runnable(0) })"""
      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }

    /** Macro for [[RunLoop.startNow]] */
    def startNow(c: Context)(runnable: c.Expr[FrameId => Unit])(s: c.Expr[Scheduler]): c.Expr[Unit] = {
      import c.universe._
      val tree = q"""$runnable(1)"""
      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }

    /** Macro for [[RunLoop.step]] */
    def step(c: Context)(frameId: c.Expr[FrameId])
      (runnable: c.Expr[FrameId => Unit])(s: c.Expr[Scheduler]): c.Expr[Unit] = {

      import c.universe._
      val util = SyntaxUtil[c.type](c)
      val nextFrameId = util.name("nextFrameId")

      val tree =
        if (util.isClean(s, runnable)) {
          q"""
          val $nextFrameId = ($frameId + 1) & $s.batchedExecutionModulus
          if ($nextFrameId > 0)
            $runnable($nextFrameId)
          else
            $s.execute(new Runnable { def run(): Unit = $runnable(0) })
          """
        } else {
          val ec = util.name("ec")
          val fn = util.name("fn")
          q"""
          val $ec = $s
          val $fn = $runnable
          val $nextFrameId = ($frameId + 1) & $ec.batchedExecutionModulus
          if ($nextFrameId > 0)
            $fn($nextFrameId)
          else
            $ec.execute(new Runnable { def run(): Unit = $fn(0) })
          """
        }

      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }

    /** Macro for [[RunLoop.stepInterruptibly]] */
    def stepInterruptibly(c: Context)
      (active: c.Expr[BooleanCancelable], frameId: c.Expr[FrameId])
      (runnable: c.Expr[FrameId => Unit])(s: c.Expr[Scheduler]): c.Expr[Unit] = {

      import c.universe._
      val util = SyntaxUtil[c.type](c)
      val nextFrameId = util.name("nextFrameId")

      val tree =
        if (util.isClean(active, s, runnable)) {
          q"""
          val $nextFrameId = ($frameId + 1) & $s.batchedExecutionModulus
          if ($nextFrameId > 0)
            $runnable($nextFrameId)
          else if (!$active.isCanceled)
            $s.execute(new Runnable { def run(): Unit = $runnable(0) })
          """
        } else {
          val ec = util.name("ec")
          val fn = util.name("fn")
          val cancelable = util.name("cancelable")
          q"""
          val $ec = $s
          val $fn = $runnable
          val $cancelable = $active

          val $nextFrameId = ($frameId + 1) & $ec.batchedExecutionModulus
          if ($nextFrameId > 0)
            $fn($nextFrameId)
          else if (!$cancelable.isCanceled)
            $ec.execute(new Runnable { def run(): Unit = $fn(0) })
          """
        }

      new InlineUtil[c.type](c).inlineAndReset[Unit](tree)
    }
  }
}
