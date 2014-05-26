package monifu.concurrent.schedulers

import scala.concurrent.ExecutionContext
import java.util.concurrent._
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.{SchedulerCompanion, Scheduler}


private[concurrent] trait SchedulerCompanionImpl extends SchedulerCompanion {
  object Implicits extends ImplicitsType {
    implicit def global =
      SchedulerCompanionImpl.this.computation

    implicit def trampoline =
      SchedulerCompanionImpl.this.trampoline
  }

  def computation: ConcurrentScheduler =
    ConcurrentScheduler.defaultInstance

  lazy val io: ConcurrentScheduler = {
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

  lazy val trampoline =
    new TrampolineScheduler(
      ConcurrentScheduler.defaultInstance,
      ConcurrentScheduler.defaultInstance.reportFailure
    )

  def trampoline(fallback: ConcurrentScheduler) =
    new TrampolineScheduler(
      fallback,
      fallback.reportFailure
    )

  def fromExecutor(executor: Executor): Scheduler =
    ConcurrentScheduler(ExecutionContext.fromExecutor(executor))

  def fromExecutorService(executor: ExecutorService): Scheduler =
    ConcurrentScheduler(ExecutionContext.fromExecutorService(executor))

  def fromContext(schedulerService: ScheduledExecutorService, ec: ExecutionContext): ConcurrentScheduler =
    ConcurrentScheduler(schedulerService, ec)

  def fromContext(implicit ec: ExecutionContext): ConcurrentScheduler =
    ec match {
      case ref: ConcurrentScheduler => ref
      case _ => ConcurrentScheduler(ec)
    }
}
