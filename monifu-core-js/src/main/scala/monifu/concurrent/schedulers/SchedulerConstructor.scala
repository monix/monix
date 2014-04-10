package monifu.concurrent.schedulers

import monifu.concurrent.Scheduler

private[concurrent] trait SchedulerConstructor {
  object Implicits {
    implicit def computation: Scheduler =
      SchedulerConstructor.this.computation
  }

  def computation: Scheduler =
    JSAsyncScheduler
}
