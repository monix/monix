package monifu.concurrent.schedulers

import monifu.concurrent.cancelables.MultiAssignmentCancelable
import monifu.concurrent.{Cancelable, Scheduler}
import scala.concurrent.duration._

abstract class ReferenceScheduler extends Scheduler {
  def nanoTime(): Long = System.nanoTime()

  def execute(action: => Unit): Unit = {
    execute(new Runnable { def run(): Unit = action })
  }

  def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable =
    scheduleOnce(initialDelay, new Runnable {
      def run(): Unit = action
    })

  def scheduleFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration, r: Runnable): Cancelable = {
    val sub = MultiAssignmentCancelable()

    def loop(initialDelay: FiniteDuration, delay: FiniteDuration): Unit =
      sub := scheduleOnce(initialDelay, new Runnable {
        def run(): Unit = {
          r.run()
          loop(delay, delay)
        }
      })

    loop(initialDelay, delay)
    sub
  }

  def scheduleFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(action: => Unit): Cancelable =
    scheduleFixedDelay(initialDelay, delay, new Runnable {
      def run(): Unit = action
    })

  def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration, r: Runnable): Cancelable = {
    val sub = MultiAssignmentCancelable()

    def loop(initialDelay: FiniteDuration): Unit = {
      val startedAt = nanoTime()

      sub := scheduleOnce(initialDelay, new Runnable {
        def run(): Unit = {
          r.run()

          val delay = {
            val duration = (nanoTime() - startedAt).nanos
            val d = period - duration
            if (d >= Duration.Zero) d else Duration.Zero
          }

          loop(delay)
        }
      })
    }


    loop(initialDelay)
    sub
  }

  def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(action: => Unit): Cancelable =
    scheduleAtFixedRate(initialDelay, period, new Runnable {
      def run(): Unit = action
    })

  /**
   * Runs a block of code in this `ExecutionContext`.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit
}
