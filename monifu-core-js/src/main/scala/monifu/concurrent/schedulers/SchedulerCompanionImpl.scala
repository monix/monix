package monifu.concurrent.schedulers

import monifu.concurrent.{Scheduler, SchedulerCompanion}
import scala.concurrent.ExecutionContext

private[concurrent] trait SchedulerCompanionImpl extends SchedulerCompanion {
  object Implicits extends ImplicitsType {
    implicit def global: Scheduler =
      AsyncScheduler
  }

  val async: Scheduler = AsyncScheduler

  val possiblyImmediate: Scheduler =
    new PossiblyImmediateScheduler(AsyncScheduler, AsyncScheduler.reportFailure)

  def fromContext(implicit ec: ExecutionContext): Scheduler =
    new ContextScheduler(ec)
}
