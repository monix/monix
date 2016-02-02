/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.execution.schedulers

import monix.execution.{SchedulerCompanion, Scheduler, UncaughtExceptionReporter}
import monix.execution.UncaughtExceptionReporter.LogExceptionsToStandardErr

private[execution] class SchedulerCompanionImpl extends SchedulerCompanion {
  /**
    * [[Scheduler]] builder.
    *
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    */
  def apply(reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    AsyncScheduler(reporter)
  }

  /**
    * Builds a [[monix.execution.schedulers.TrampolineScheduler TrampolineScheduler]].
    *
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    */
  def trampoline(reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr): Scheduler = {
    TrampolineScheduler(reporter)
  }

  /** The explicit global `Scheduler`. Invoke `global` when you want to provide the global
    * `Scheduler` explicitly.
    */
  def global: Scheduler = Implicits.global

  object Implicits extends ImplicitsLike {
    /**
      * A global [[Scheduler]] instance, provided for convenience, piggy-backing
      * on top of `global.setTimeout`.
      */
    implicit lazy val global: Scheduler =
      AsyncScheduler(UncaughtExceptionReporter.LogExceptionsToStandardErr)
  }
}
