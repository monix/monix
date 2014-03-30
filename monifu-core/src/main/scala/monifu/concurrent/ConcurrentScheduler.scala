package monifu.concurrent

import java.util.concurrent.{ThreadFactory, Executors, TimeUnit, ScheduledExecutorService}
import scala.concurrent.ExecutionContext
import monifu.concurrent.atomic.Atomic
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.cancelables.{CompositeCancelable, BooleanCancelable}


final class ConcurrentScheduler private (s: ScheduledExecutorService, ec: ExecutionContext) extends Scheduler {
  def scheduleOnce(action: => Unit): Cancelable = {
    val isCancelled = Atomic(false)
    val sub = BooleanCancelable(isCancelled := true)

    ec.execute(new Runnable {
      def run(): Unit =
        if (!isCancelled.get) action
    })

    sub
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    val isCancelled = Atomic(false)
    val sub = CompositeCancelable(BooleanCancelable {
      isCancelled := true
    })

    val runnable = new Runnable {
      def run(): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            if (!isCancelled.get) action
        })
    }

    val task = s.schedule(runnable, initialDelay.toMillis, TimeUnit.MILLISECONDS)
    sub += BooleanCancelable {
      task.cancel(true)
    }

    sub
  }

  def schedule(action: Scheduler => Cancelable): Cancelable = {
    val thisScheduler = this
    val isCancelled = Atomic(false)

    val sub = CompositeCancelable {
      BooleanCancelable(isCancelled := true)
    }

    ec.execute(new Runnable {
      def run(): Unit =
        if (!isCancelled.get)
          sub += action(thisScheduler)
    })

    sub
  }

  def schedule(initialDelay: FiniteDuration, action: Scheduler => Cancelable): Cancelable = {
    val thisScheduler = this
    val isCancelled = Atomic(false)

    val sub = CompositeCancelable(BooleanCancelable {
      isCancelled := true
    })

    val runnable = new Runnable {
      def run(): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            if (!isCancelled.get)
              sub += action(thisScheduler)
        })
    }

    val task = s.schedule(runnable, initialDelay.toMillis, TimeUnit.MILLISECONDS)

    sub += BooleanCancelable {
      task.cancel(true)
    }

    sub
  }

  /**
   * Runs a block of code in this ExecutionContext.
   * Inherited from ExecutionContext.
   */
  def execute(runnable: Runnable): Unit =
    ec.execute(runnable)

  /**
   * Reports that an asynchronous computation failed.
   * Inherited from ExecutionContext.
   */
  def reportFailure(t: Throwable): Unit =
    ec.reportFailure(t)
}

object ConcurrentScheduler {
  private[this] lazy val defaultScheduledExecutor = Executors.newScheduledThreadPool(1, new ThreadFactory {
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
