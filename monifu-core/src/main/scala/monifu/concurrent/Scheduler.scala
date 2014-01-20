package monifu.concurrent

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.util.concurrent.{ScheduledExecutorService, TimeUnit, ThreadFactory, Executors}
import scala.annotation.implicitNotFound
import scala.util.control.NonFatal

/**
 * Scheduler is a service that can schedule commands to run after a given delay,
 * or to execute periodically.
 *
 * Example:
 * {{{
 *   import monifu.concurrent.Scheduler
 *   import scala.concurrent.duration._
 *   // implicit execution context for actually running the tasks
 *   import scala.concurrent.ExecutionContext.Implicits.global
 *
 *   val scheduler = Scheduler("my-scheduler")
 *
 *   scheduler.scheduleOnce(10.seconds) {
 *     println("Hello, world!")
 *   }
 *
 *   scheduler.schedule(initialDelay = 10.seconds, period = 30.seconds) {
 *     println("Hello I say, every 30 seconds!")
 *   }
 * }}}
 */
@implicitNotFound(
  "An implicit Scheduler was not found in scope, create your own " +
  "instance or import monifu.concurrent.Implicits.defaultScheduler"
)
trait Scheduler {
  /**
   * Executes given action after the specified delay.
   */
  def scheduleOnce(delay: FiniteDuration)(cb: => Unit)(implicit ec: ExecutionContext): Cancellable

  /**
   * Creates and executes a periodic action that is triggered first
   * after the given initial delay, and subsequently with the given period.
   */
  def schedule(initialDelay: FiniteDuration, period: FiniteDuration)(cb: => Unit)(implicit ec: ExecutionContext): Cancellable

  /**
   * Executes given action after the specified delay, with the result being
   * available for consumption as a Future.
   */
  def future[A](delay: FiniteDuration)(cb: => A)(implicit ec: ExecutionContext): Future[A] = {
    val promise = Promise[A]()
    scheduleOnce(delay) {
      try {
        promise.trySuccess(cb)
      }
      catch {
        case NonFatal(ex) =>
          promise.tryFailure(ex)
      }
    }

    promise.future
  }

  /**
   * Shuts down the scheduler.
   */
  def shutdown(): Unit
}

object Scheduler {
  def apply(name: String): Scheduler =
    JavaScheduler(name)
}

class JavaScheduler private (pool: ScheduledExecutorService) extends Scheduler {
  def scheduleOnce(delay: FiniteDuration)(cb: => Unit)(implicit ec: ExecutionContext): Cancellable = {
    shutdownCheck()
    val run = Runnable(ec.execute(Runnable(cb)))
    val task = pool.schedule(run, delay.toMillis, TimeUnit.MILLISECONDS)
    Cancellable(task.cancel(false))
  }

  def schedule(initialDelay: FiniteDuration, period: FiniteDuration)(cb: => Unit)(implicit ec: ExecutionContext): Cancellable = {
    shutdownCheck()
    val run = Runnable(ec.execute(Runnable(cb)))
    val task = pool.scheduleAtFixedRate(run, initialDelay.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
    Cancellable(task.cancel(false))
  }

  def shutdown(): Unit = {
    isShutdown = true
    pool.shutdown()
  }

  private[this] def shutdownCheck(): Unit = {
    if (isShutdown)
      throw new IllegalStateException(
        "Cannot schedule task, because Scheduler instance has been shutdown")
  }

  @volatile
  private[this] var isShutdown = false
}

object JavaScheduler {
  def apply(pool: ScheduledExecutorService): Scheduler = {
    require(!pool.isShutdown, "the given ScheduledExecutorService mustn't be shutdown")
    new JavaScheduler(pool)
  }

  def apply(name: String): Scheduler = {
    val pool =
      Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val th = new Thread(r, name)
          th.setDaemon(true)
          th
        }
      })

    new JavaScheduler(pool)
  }
}



