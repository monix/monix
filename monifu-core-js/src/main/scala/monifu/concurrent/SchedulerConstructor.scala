package monifu.concurrent

private[concurrent] trait SchedulerConstructor {
  object Implicits {
    implicit lazy val global: Scheduler =
      JSAsyncScheduler
  }

  def async: Scheduler =
    JSAsyncScheduler
}
