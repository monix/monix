package monifu.concurrent

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import monifu.concurrent.cancelables.MultiAssignmentCancelable

trait Scheduler extends ExecutionContext {
  def scheduleOnce(action: => Unit): Cancelable

  def scheduleOnce(delayTime: FiniteDuration, action: => Unit): Cancelable

  def scheduleOnce(action: Scheduler => Cancelable): Cancelable

  def scheduleOnce(delayTime: FiniteDuration, action: Scheduler => Cancelable): Cancelable

  def scheduleRepeated(initialDelay: FiniteDuration, period: FiniteDuration, action: => Unit): Cancelable =
    scheduleRepeated(initialDelay, period, { reschedule =>
      action
      reschedule()
    })

  def scheduleRepeated(initialDelay: FiniteDuration, period: FiniteDuration, cb: (() => Unit) => Unit): Cancelable = {
    val sub = MultiAssignmentCancelable()
    val startedAtNanos = System.nanoTime()

    def reschedule() = {
      val timeTaken = (System.nanoTime() - startedAtNanos).nanos
      sub() = scheduleRepeated(period - timeTaken + initialDelay, period, cb)
    }

    sub() = scheduleOnce(initialDelay, { if (!sub.isCanceled) cb(reschedule) })
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

