package monifu.concurrent

private[concurrent] trait SchedulerConstructor {
  def async: Scheduler =
    JSAsyncScheduler
}
