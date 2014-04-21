package monifu.concurrent.schedulers

import monifu.concurrent.{Scheduler, SchedulerCompanion}
import scala.concurrent.ExecutionContext

private[concurrent] trait SchedulerCompanionImpl extends SchedulerCompanion {
  object Implicits extends ImplicitsType {
    implicit def global: Scheduler =
      AsyncScheduler

    implicit def trampoline: Scheduler =
      SchedulerCompanionImpl.this.trampoline
  }

  val async: Scheduler = AsyncScheduler

  val trampoline: Scheduler = TrampolineScheduler(AsyncScheduler)

  def fromContext(implicit ec: ExecutionContext): Scheduler =
    new ContextScheduler(ec)
}
