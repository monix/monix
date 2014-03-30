package monifu.concurrent

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import monifu.concurrent.cancelables.MultiAssignmentCancelable
import scala.annotation.implicitNotFound

@implicitNotFound("Cannot find an implicit Scheduler, either import monifu.concurrent.Scheduler.Implicits.global or use a custom one")
trait Scheduler extends ExecutionContext {
  def schedule(action: Scheduler => Cancelable): Cancelable

  def schedule(initialDelay: FiniteDuration, action: Scheduler => Cancelable): Cancelable

  def scheduleOnce(action: => Unit): Cancelable

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable

  def schedulePeriodically(initialDelay: FiniteDuration, period: FiniteDuration, action: => Unit): Cancelable =
    scheduleRec(initialDelay, period, { reschedule =>
      action
      reschedule()
    })

  def scheduleRec(initialDelay: FiniteDuration, period: FiniteDuration, action: (() => Unit) => Unit): Cancelable = {
    val sub = MultiAssignmentCancelable()
    val startedAtNanos = System.nanoTime()
    def reschedule() = {
      val timeTaken = (System.nanoTime() - startedAtNanos).nanos
      sub() = scheduleRec(period - timeTaken + initialDelay, period, action)
    }

    sub() = scheduleOnce(initialDelay, { if (!sub.isCanceled) action(reschedule) })
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

object Scheduler extends SchedulerConstructor

