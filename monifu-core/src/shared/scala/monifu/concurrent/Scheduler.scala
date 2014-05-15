package monifu.concurrent

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import monifu.concurrent.cancelables.{BooleanCancelable, MultiAssignmentCancelable}
import scala.annotation.implicitNotFound
import monifu.concurrent.schedulers.SchedulerCompanionImpl

/**
 * A Scheduler is an `scala.concurrent.ExecutionContext` that additionally can schedule
 * the execution of units of work to run with a delay or periodically.
 */
@implicitNotFound("Cannot find an implicit Scheduler, either import monifu.concurrent.Scheduler.Implicits.global or use a custom one")
trait Scheduler extends ExecutionContext {
  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable

  def scheduleOnce(action: => Unit): Cancelable = {
    val sub = BooleanCancelable()
    execute(new Runnable {
      def run(): Unit =
        if (!sub.isCanceled)
          action
    })

    sub
  }

  def schedule(action: Scheduler => Cancelable): Cancelable = {
    val sub = MultiAssignmentCancelable()
    sub := scheduleOnce(sub := action(Scheduler.this))
    sub
  }

  def schedule(initialDelay: FiniteDuration, action: Scheduler => Cancelable): Cancelable = {
    val sub = MultiAssignmentCancelable()
    sub := scheduleOnce(initialDelay, {
      sub := action(Scheduler.this)
    })
    sub
  }

  def scheduleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration, action: => Unit): Cancelable =
    scheduleRecursive(initialDelay, delay, { reschedule =>
      action
      reschedule()
    })

  def scheduleRecursive(initialDelay: FiniteDuration, delay: FiniteDuration, action: (() => Unit) => Unit): Cancelable = {
    val sub = MultiAssignmentCancelable()

    def reschedule(): Unit =
      sub() = scheduleOnce(delay, action(reschedule))

    sub() = scheduleOnce(initialDelay, action(reschedule))
    sub
  }

  /**
   * Runs a block of code in this ExecutionContext.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit
}

private[concurrent] trait SchedulerCompanion {
  trait ImplicitsType {
    implicit def global: Scheduler
    implicit def trampoline: Scheduler
  }

  def Implicits: ImplicitsType
  def fromContext(implicit ec: ExecutionContext): Scheduler
  def trampoline: Scheduler
}

object Scheduler extends SchedulerCompanion with SchedulerCompanionImpl

