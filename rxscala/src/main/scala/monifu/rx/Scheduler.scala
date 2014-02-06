package monifu.rx

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import monifu.rx.schedulers.ConcurrentScheduler


trait Scheduler extends ExecutionContext {
  def schedule(action: => Unit): Subscription

  def schedule(delayTime: FiniteDuration)(action: => Unit): Subscription

  def schedule(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Subscription = {
    val sub = MultiAssignmentSubscription()
    val startedAt = System.currentTimeMillis()

    sub() = schedule(initialDelay) {
      action
      val timeTaken = (System.currentTimeMillis() - startedAt).millis
      sub() = schedule(period - timeTaken + initialDelay, period)(action)
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

object Scheduler {
  def concurrent: ConcurrentScheduler =
    ConcurrentScheduler.defaultInstance

  def concurrent(ec: ExecutionContext): ConcurrentScheduler =
    ConcurrentScheduler(ec)
}


