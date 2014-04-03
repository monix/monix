package monifu.concurrent.schedulers

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import monifu.concurrent.{Cancelable, Scheduler}


object JSAsyncScheduler extends Scheduler {
  def scheduleOnce(action: => Unit): Cancelable = {
    var isCancelled = false
    val sub = Cancelable { isCancelled = true }
    setTimeout(if (!isCancelled) action)
    sub
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    var isCancelled = false
    val task = setTimeout(initialDelay.toMillis, {
      if (!isCancelled) action
    })

    Cancelable { isCancelled = true; clearTimeout(task) }
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
