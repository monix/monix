package monifu.rx.schedulers

import java.util.concurrent.{ThreadFactory, Executors, TimeUnit, ScheduledExecutorService}
import scala.concurrent.ExecutionContext
import monifu.rx.{Scheduler, Subscription}
import monifu.concurrent.atomic.Atomic
import scala.concurrent.duration.FiniteDuration
import monifu.rx.subscriptions.{CompositeSubscription, BooleanSubscription}


final class ConcurrentScheduler private (s: ScheduledExecutorService, ec: ExecutionContext) extends Scheduler {
  def schedule(action: => Unit): Subscription = {
    val isCancelled = Atomic(false)
    val sub = BooleanSubscription(isCancelled := true)

    ec.execute(new Runnable {
      def run(): Unit =
        if (!isCancelled.get) action
    })

    sub
  }

  def schedule(delayTime: FiniteDuration)(action: => Unit): Subscription = {
    val isCancelled = Atomic(false)
    val sub = CompositeSubscription(BooleanSubscription {
      isCancelled := true
    })

    val runnable = new Runnable {
      def run(): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            if (!isCancelled.get) action
        })
    }

    val task = s.schedule(runnable, delayTime.toMillis, TimeUnit.MILLISECONDS)
    sub += BooleanSubscription {
      task.cancel(true)
    }

    sub
  }

  def scheduleR(action: Scheduler => Subscription): Subscription = {
    val thisScheduler = this
    val isCancelled = Atomic(false)

    val sub = CompositeSubscription {
      BooleanSubscription(isCancelled := true)
    }

    ec.execute(new Runnable {
      def run(): Unit =
        if (!isCancelled.get)
          sub += action(thisScheduler)
    })

    sub
  }

  def scheduleR(delayTime: FiniteDuration)(action: Scheduler => Subscription): Subscription = {
    val thisScheduler = this
    val isCancelled = Atomic(false)

    val sub = CompositeSubscription(BooleanSubscription {
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

    val task = s.schedule(runnable, delayTime.toMillis, TimeUnit.MILLISECONDS)

    sub += BooleanSubscription {
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
      th.setName("monifu-rx-scheduler")
      th
    }
  })

  def apply(implicit ec: ExecutionContext): ConcurrentScheduler =
    new ConcurrentScheduler(defaultScheduledExecutor, ec)

  lazy val defaultInstance =
    new ConcurrentScheduler(defaultScheduledExecutor, ExecutionContext.Implicits.global)
}

