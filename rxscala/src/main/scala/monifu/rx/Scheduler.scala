package monifu.rx

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import java.util.concurrent._
import monifu.concurrent.atomic.Atomic
import java.util.concurrent.TimeUnit


trait Scheduler extends ExecutionContext {
  def schedule(action: => Unit): Subscription

  def schedule(delayTime: FiniteDuration)(action: => Unit): Subscription

  def schedule(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Subscription = {
    val sub = MultiAssignmentSubscription()
    val startedAt = System.currentTimeMillis()

    sub := schedule(initialDelay) {
      action
      val timeTaken = (System.currentTimeMillis() - startedAt).millis
      sub := schedule(period - timeTaken + initialDelay, period)(action)
    }

    sub
  }

  def scheduleR(action: Scheduler => Subscription): Subscription

  def scheduleR(delayTime: FiniteDuration)(action: Scheduler => Subscription): Subscription

  /**
   * Runs a block of code in this ExecutionContext.
   * Inherited from ExecutionContext.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   * Inherited from ExecutionContext.
   */
  def reportFailure(t: Throwable): Unit
}

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
  
  def apply(s: ScheduledExecutorService)(implicit ec: ExecutionContext): ConcurrentScheduler =
    new ConcurrentScheduler(s, ec)

  lazy val defaultInstance =
    apply(defaultScheduledExecutor)(ExecutionContext.Implicits.global)
}

