package monifu.concurrent

import scala.concurrent.ExecutionContext

private[concurrent] trait SchedulerConstructor {
  def concurrent(implicit ec: ExecutionContext): Scheduler =
    ConcurrentScheduler(ec)
}
