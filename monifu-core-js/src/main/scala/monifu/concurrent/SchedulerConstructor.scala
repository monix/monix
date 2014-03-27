package monifu.concurrent

private[concurrent] trait SchedulerConstructor {
  def asyncScheduler: Scheduler =
    JSAsyncScheduler
}
