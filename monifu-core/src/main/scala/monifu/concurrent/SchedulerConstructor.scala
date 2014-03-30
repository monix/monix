package monifu.concurrent

import scala.concurrent.ExecutionContext

private[concurrent] trait SchedulerConstructor {
  object Implicits {
    implicit lazy val global: Scheduler =
      concurrent(ExecutionContext.Implicits.global)
  }

  def concurrent(implicit ec: ExecutionContext): Scheduler =
    ConcurrentScheduler(ec)
}
