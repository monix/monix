package monifu.concurrent.schedulers

import scala.concurrent.ExecutionContext
import java.util.concurrent._
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Scheduler

private[concurrent] trait SchedulerConstructor {
  object Implicits {
    implicit lazy val computation: Scheduler =
      SchedulerConstructor.this.computation

    implicit lazy val io: Scheduler =
      SchedulerConstructor.this.io
  }

  lazy val computation =
    ConcurrentScheduler(ExecutionContext.Implicits.global)

  lazy val io = {
    val counter = Atomic(0L)
    ConcurrentScheduler(ExecutionContext.fromExecutor(
      Executors.newCachedThreadPool(new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(true)
          th.setName("monifu-io-" + counter.getAndIncrement().toString)
          th
        }
      })
    ))
  }

  def fromExecutor(executor: Executor): Scheduler =
    ConcurrentScheduler(ExecutionContext.fromExecutor(executor))

  def fromExecutorService(executor: ExecutorService): Scheduler =
    ConcurrentScheduler(ExecutionContext.fromExecutorService(executor))

  def concurrent(schedulerService: ScheduledExecutorService, ec: ExecutionContext): Scheduler =
    ConcurrentScheduler(schedulerService, ec)

  def concurrent(implicit ec: ExecutionContext): Scheduler =
    ConcurrentScheduler(ec)
}
