/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import monix.execution.{Scheduler, SchedulerCompanion, UncaughtExceptionReporter}
import monix.execution.UncaughtExceptionReporter.LogExceptionsToStandardErr
import monix.execution.{ExecutionModel => ExecModel}

private[execution] class SchedulerCompanionImpl extends SchedulerCompanion {
  /** [[monix.execution.Scheduler Scheduler]] builder.
    *
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    * @param executionModel is the preferred
    *        [[monix.execution.ExecutionModel ExecutionModel]],
    *        a guideline for run-loops and producers of data.
    */
  def apply(
    reporter: UncaughtExceptionReporter = LogExceptionsToStandardErr,
    executionModel: ExecModel = ExecModel.Default): Scheduler =
    AsyncScheduler(reporter, executionModel)

  /** Builds a [[monix.execution.schedulers.TrampolineScheduler TrampolineScheduler]].
    *
    * @param underlying is the [[monix.execution.Scheduler Scheduler]]
    *        to which the we defer to in case asynchronous or time-delayed
    *        execution is needed
    *
    * @define executionModel is the preferred
    *         [[monix.execution.ExecutionModel ExecutionModel]],
    *         a guideline for run-loops and producers of data. Use
    *         [[monix.execution.ExecutionModel.Default ExecutionModel.Default]]
    *         for the default.
    */
  def trampoline(
    underlying: Scheduler = Implicits.global,
    executionModel: ExecModel = ExecModel.Default): Scheduler =
    TrampolineScheduler(underlying, executionModel)

  /** The explicit global `Scheduler`. Invoke `global` when you want
    * to provide the global `Scheduler` explicitly.
    */
  def global: Scheduler = Implicits.global

  /** A global [[monix.execution.Scheduler Scheduler]] instance that does
    * propagation of [[monix.execution.misc.Local.Context Local.Context]]
    * on async execution.
    *
    * It wraps [[global]].
    */
  def traced: Scheduler = Implicits.traced

  object Implicits extends ImplicitsLike {
    /** A global [[monix.execution.Scheduler Scheduler]] instance,
      * provided for convenience, piggy-backing
      * on top of `global.setTimeout`.
      */
    implicit lazy val global: Scheduler =
      AsyncScheduler(
        UncaughtExceptionReporter.LogExceptionsToStandardErr,
        ExecModel.Default)

    /** A [[monix.execution.Scheduler Scheduler]] instance that does
      * propagation of [[monix.execution.misc.Local.Context Local.Context]]
      * through async execution.
      *
      * It wraps [[global]].
      */
    implicit lazy val traced: Scheduler =
      TracingScheduler(global)
  }
}
