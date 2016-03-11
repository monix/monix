package monix.execution.schedulers

import java.util.concurrent.ScheduledExecutorService
import monix.execution.{Cancelable, UncaughtExceptionReporter}
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

/** An [[ExecutorScheduler]] is for building a
  * [[monix.execution.Scheduler Scheduler]] out of a `ScheduledExecutorService`.
  */
final class ExecutorScheduler private (s: ScheduledExecutorService, r: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  def executor: ScheduledExecutorService = s

  def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable = {
    scheduleOnce(initialDelay.length, initialDelay.unit, r)
  }

  def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable) = {
    if (initialDelay <= 0) {
      execute(r)
      Cancelable.empty
    }
    else {
      val task = s.schedule(r, initialDelay, unit)
      Cancelable(() => task.cancel(true))
    }
  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleWithFixedDelay(r, initialDelay, delay, unit)
    Cancelable(() => task.cancel(false))
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleAtFixedRate(r, initialDelay, period, unit)
    Cancelable(() => task.cancel(false))
  }

  def execute(runnable: Runnable): Unit =
    s.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)
}

object ExecutorScheduler {
  /** Builder for [[ExecutorScheduler]]. */
  def apply(schedulerService: ScheduledExecutorService,
    reporter: UncaughtExceptionReporter): ExecutorScheduler =
    new ExecutorScheduler(schedulerService, reporter)
}