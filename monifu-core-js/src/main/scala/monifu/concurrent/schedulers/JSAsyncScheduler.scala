package monifu.concurrent.schedulers

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import monifu.concurrent.cancelables.{CompositeCancelable, BooleanCancelable}
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.{Cancelable, Scheduler}


object JSAsyncScheduler extends Scheduler {
  def scheduleOnce(action: => Unit): Cancelable = {
    val isCancelled = Atomic(false)
    val sub = BooleanCancelable(isCancelled := true)

    setTimeout(if (!isCancelled.get) action)
    sub
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    val isCancelled = Atomic(false)
    val sub = CompositeCancelable(BooleanCancelable {
      isCancelled := true
    })

    val task = setTimeout(initialDelay.toMillis, {
      if (!isCancelled.get) action
    })

    sub += BooleanCancelable(clearTimeout(task))
    sub
  }

  def schedule(action: Scheduler => Cancelable): Cancelable = {
    val thisScheduler = this
    val isCancelled = Atomic(false)

    val sub = CompositeCancelable(BooleanCancelable(isCancelled := true))
    setTimeout {
      if (!isCancelled.get)
        sub += action(thisScheduler)
    }

    sub
  }

  def schedule(initialDelay: FiniteDuration, action: Scheduler => Cancelable): Cancelable = {
    val thisScheduler = this
    val isCancelled = Atomic(false)

    val sub = CompositeCancelable(BooleanCancelable(isCancelled := true))
    val task = setTimeout(initialDelay.toMillis, {
      if (!isCancelled.get)
        sub += action(thisScheduler)
    })

    sub += BooleanCancelable(clearTimeout(task))
    sub
  }

  def reportFailure(t: Throwable): Unit =
    Console.err.println("Failure in async execution: " + t)

  def execute(runnable: Runnable): Unit = {
    val lambda: js.Function = () =>
      try { runnable.run() } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, 0)
  }

  private[this] def setTimeout(cb: => Unit): js.Dynamic = {
    val lambda: js.Function = () =>
      try { cb } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, 0)
  }

  private[this] def setTimeout(delayMillis: Long, cb: => Unit): js.Dynamic = {
    val lambda: js.Function = () =>
      try { cb } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  private[this] def clearTimeout(task: js.Dynamic) = {
    js.Dynamic.global.clearTimeout(task)
  }
}
