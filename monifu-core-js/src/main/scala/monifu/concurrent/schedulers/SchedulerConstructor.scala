package monifu.concurrent.schedulers

import monifu.concurrent.Scheduler

private[concurrent] trait SchedulerConstructor {
  object Implicits {
    implicit lazy val computation: Scheduler =
      SchedulerConstructor.this.computation
  }

  def computation: Scheduler =
    JSAsyncScheduler
}
