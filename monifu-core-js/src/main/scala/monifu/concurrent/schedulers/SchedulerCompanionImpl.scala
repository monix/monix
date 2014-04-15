package monifu.concurrent.schedulers

import monifu.concurrent.{Scheduler, SchedulerCompanion}
import scala.concurrent.ExecutionContext

private[concurrent] trait SchedulerCompanionImpl extends SchedulerCompanion {
  object Implicits extends ImplicitsType {
    implicit def global: Scheduler =
      JSAsyncScheduler

    implicit def computation: Scheduler =
      JSAsyncScheduler

    implicit def io: Scheduler =
      JSAsyncScheduler
  }

  def fromContext(implicit ec: ExecutionContext): Scheduler =
    new ContextScheduler(ec)
}
