package monifu.concurrent.schedulers

import java.util.concurrent.{ThreadFactory, Executors, TimeUnit, ScheduledExecutorService}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.{Cancelable, Scheduler}


private[concurrent] final class ConcurrentScheduler(s: ScheduledExecutorService, ec: ExecutionContext) extends Scheduler {
  def scheduleOnce(action: => Unit): Cancelable = {
    val sub = Cancelable()

    ec.execute(new Runnable {
      def run(): Unit =
        if (!sub.isCanceled) action
    })

    sub
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable =
    if (initialDelay <= Duration.Zero)
      scheduleOnce(action)
    else {
      val sub = SingleAssignmentCancelable()

      val runnable = new Runnable {
        def run(): Unit =
          ec.execute(new Runnable {
            def run(): Unit =
              if (!sub.isCanceled) action
          })
      }

      val task =
        if (initialDelay < oneHour)
          s.schedule(runnable, initialDelay.toNanos, TimeUnit.NANOSECONDS)
        else
          s.schedule(runnable, initialDelay.toMillis, TimeUnit.MILLISECONDS)

      sub := Cancelable(task.cancel(true))
      sub
    }

  /**
   * Overwritten for performance reasons.
   */
  override def scheduleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration, action: => Unit): Cancelable = {
    @volatile var isCanceled = false
    val runnable = new Runnable {
      def run(): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            if (!isCanceled) action
        })
    }

    val task = s.scheduleWithFixedDelay(runnable, initialDelay.toMillis, delay.toMillis, TimeUnit.MILLISECONDS)
    Cancelable { isCanceled = true; task.cancel(false) }
  }

  def execute(runnable: Runnable): Unit =
    ec.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    ec.reportFailure(t)

  private[this] val oneHour = 1.hour
}

private[concurrent] object ConcurrentScheduler {
  private[this] lazy val defaultScheduledExecutor =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val th = new Thread(r)
        th.setDaemon(true)
        th.setName("monifu-scheduler")
        th
      }
    })

  def apply(schedulerService: ScheduledExecutorService, ec: ExecutionContext): ConcurrentScheduler =
    new ConcurrentScheduler(schedulerService, ec)

  def apply(implicit ec: ExecutionContext): ConcurrentScheduler =
    new ConcurrentScheduler(defaultScheduledExecutor, ec)

  lazy val defaultInstance =
    new ConcurrentScheduler(defaultScheduledExecutor, ExecutionContext.Implicits.global)
}
