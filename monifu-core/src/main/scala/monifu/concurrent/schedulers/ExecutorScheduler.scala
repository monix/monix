package monifu.concurrent.schedulers

import java.util.concurrent.{ThreadFactory, Executors, ScheduledExecutorService, TimeUnit}

import monifu.concurrent.{Cancelable, Scheduler}
import monifu.concurrent.cancelables.{BooleanCancelable, SingleAssignmentCancelable}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * A [[monifu.concurrent.Scheduler Scheduler]] backed by Java's
 * `ScheduledExecutorService`.
 */
final class ExecutorScheduler private (service: ScheduledExecutorService)
  extends Scheduler {

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit)
      (implicit ec: ExecutionContext) = {
    val sub = SingleAssignmentCancelable()
    val runnable = new Runnable {
      def run() = if (!sub.isCanceled) action
    }

    if (initialDelay <= Duration.Zero) {
      ec.execute(runnable)
      sub
    }
    else {
      val task = service.schedule(
        runnable, initialDelay.toMillis, TimeUnit.MILLISECONDS)
      sub := BooleanCancelable(task.cancel(false))
      sub
    }
  }

  override def scheduleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration, action: => Unit)
      (implicit ec: ExecutionContext): Cancelable = {

    @volatile var isCanceled = false
    val runnable = new Runnable {
      def run(): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            if (!isCanceled) action
        })
    }

    val task = service.scheduleWithFixedDelay(
      runnable, initialDelay.toMillis, delay.toMillis, TimeUnit.MILLISECONDS)
    Cancelable { isCanceled = true; task.cancel(false) }
  }
}

object ExecutorScheduler {
  def apply(): ExecutorScheduler = defaultInstance

  def apply(service: ScheduledExecutorService): ExecutorScheduler =
    new ExecutorScheduler(service)

  def apply(executorName: String): ExecutorScheduler =
    new ExecutorScheduler(createExecutorService(executorName))

  private def createExecutorService(name: String): ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val th = new Thread(r)
        th.setDaemon(true)
        th.setName(name)
        th
      }
    })

  private[concurrent] lazy val monifuScheduledExecutorService =
    createExecutorService("monifu-scheduler")
  
  private[concurrent] lazy val defaultInstance =
    new ExecutorScheduler(monifuScheduledExecutorService)
}
