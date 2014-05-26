package monifu.concurrent.schedulers

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import monifu.concurrent.{Cancelable, Scheduler}

private[concurrent] object AsyncScheduler extends Scheduler {
  override def scheduleOnce(action: => Unit): Cancelable = {
    val task = setTimeout(action)
    Cancelable(clearTimeout(task))
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    val task = setTimeout(initialDelay.toMillis, action)
    Cancelable(clearTimeout(task))
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
