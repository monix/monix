package monifu.concurrent.schedulers

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import monifu.concurrent.Scheduler.{Environment, Platform}
import monifu.concurrent.{Cancelable, UncaughtExceptionReporter}
import scala.concurrent.duration.FiniteDuration

/**
  * An [[ExecutorScheduler]] is for building a
  * [[monifu.concurrent.Scheduler Scheduler]] out of a `ScheduledExecutorService`.
  */
final class ExecutorScheduler private
  (s: ScheduledExecutorService, r: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  def executor: ScheduledExecutorService = s

  def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable = {
    scheduleOnce(initialDelay.length, initialDelay.unit, r)
  }

  def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable) = {
    if (initialDelay <= 0) {
      execute(r)
      Cancelable()
    }
    else {
      val task = s.schedule(r, initialDelay, unit)
      Cancelable(task.cancel(true))
    }
  }

  override def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration, r: Runnable): Cancelable = {
    val task = s.scheduleWithFixedDelay(r, initialDelay.toMillis, delay.toMillis, TimeUnit.MILLISECONDS)
    Cancelable(task.cancel(false))
  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleWithFixedDelay(r, initialDelay, delay, unit)
    Cancelable(task.cancel(false))
  }

  override def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration, r: Runnable): Cancelable = {
    val task = s.scheduleAtFixedRate(r, initialDelay.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
    Cancelable(task.cancel(false))
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val task = s.scheduleAtFixedRate(r, initialDelay, period, unit)
    Cancelable(task.cancel(false))
  }

  def execute(runnable: Runnable): Unit =
    s.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

  val env = Environment(512, Platform.JVM)
}

object ExecutorScheduler {
  def apply(schedulerService: ScheduledExecutorService,
    reporter: UncaughtExceptionReporter): ExecutorScheduler =
    new ExecutorScheduler(schedulerService, reporter)
}