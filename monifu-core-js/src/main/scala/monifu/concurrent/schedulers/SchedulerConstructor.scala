package monifu.concurrent.schedulers

import monifu.concurrent.Scheduler

private[concurrent] trait SchedulerConstructor {
  object Implicits {
    implicit lazy val async: Scheduler =
      SchedulerConstructor.this.async
  }

  def async: Scheduler =
    JSAsyncScheduler
}
