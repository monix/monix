package monifu.concurrent

import scala.concurrent.ExecutionContext

private[concurrent] trait SchedulerConstructor {
  def asyncScheduler(implicit ec: ExecutionContext): Scheduler =
    ConcurrentScheduler(ec)
}
