package monifu.concurrent.schedulers

import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.concurrent.UncaughtExceptionReporter._

private[concurrent] abstract class SchedulerCompanion {
  /**
   * [[Scheduler]] builder.
   *
   * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def apply(reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    AsyncScheduler(reporter)
  }

  /**
   * Builds a [[monifu.concurrent.schedulers.TrampolineScheduler TrampolineScheduler]].
   *
   * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
   */
  def trampoline(reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    TrampolineScheduler(reporter)
  }
}
