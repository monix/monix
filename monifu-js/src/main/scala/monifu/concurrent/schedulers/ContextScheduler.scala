package monifu.concurrent.schedulers

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.ExecutionContext
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.cancelables.BooleanCancelable


private[concurrent] final class ContextScheduler(ec: ExecutionContext) extends Scheduler {
  override def scheduleOnce(action: => Unit): Cancelable = {
    val cancelable = BooleanCancelable()
    execute(new Runnable {
      def run() = if (!cancelable.isCanceled) action
    })
    cancelable
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    val sub = SingleAssignmentCancelable()
    val task = setTimeout(initialDelay.toMillis, () => {
      if (!sub.isCanceled)
        execute(new Runnable {
          def run() = if (!sub.isCanceled) action
        })
    })

    sub := Cancelable(clearTimeout(task))
    sub
  }

  def execute(runnable: Runnable): Unit =
    ec.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    ec.reportFailure(t)

  private[this] def setTimeout(delayMillis: Long, cb: () => Unit): js.Dynamic = {
    val lambda: js.Function = () =>
      try { cb } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  private[this] def clearTimeout(task: js.Dynamic) = {
    js.Dynamic.global.clearTimeout(task)
  }
}
