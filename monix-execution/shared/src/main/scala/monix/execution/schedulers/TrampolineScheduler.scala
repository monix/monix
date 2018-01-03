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

import java.util.concurrent.TimeUnit
import monix.execution.{Cancelable, Scheduler}
// Prevents conflict with the deprecated symbol
import monix.execution.{ExecutionModel => ExecModel}

/** A [[monix.execution.Scheduler Scheduler]] implementation
  * that executes runnables immediately, on the current thread,
  * by means of a trampoline implementation.
  *
  * Can be used in some cases to keep the asynchronous execution
  * on the current thread, as an optimization, but be warned,
  * you have to know what you're doing.
  *
  * The `TrampolineScheduler` keeps a reference to another
  * `underlying` scheduler, to which it defers for:
  *
  *  - reporting errors
  *  - time-delayed execution
  *  - deferring the rest of the queue in problematic situations
  *
  * Deferring the rest of the queue happens:
  *
  *  - in case we have a runnable throwing an exception, the rest
  *    of the tasks get re-scheduled for execution by using
  *    the `underlying` scheduler
  *  - in case we have a runnable triggering a Scala `blocking`
  *    context, the rest of the tasks get re-scheduled for execution
  *    on the `underlying` scheduler to prevent any deadlocks
  *
  * Thus this implementation is compatible with the
  * `scala.concurrent.BlockContext`, detecting `blocking` blocks and
  * reacting by forking the rest of the queue to prevent deadlocks.
  *
  * @param underlying is the `ExecutionContext` to which the it defers
  *        to in case real asynchronous is needed
  */
final class TrampolineScheduler(
  underlying: Scheduler,
  override val executionModel: ExecModel)
  extends Scheduler { self =>

  private[this] val trampoline =
    TrampolineExecutionContext(underlying)
  override def execute(runnable: Runnable): Unit =
    trampoline.execute(runnable)

  override def reportFailure(t: Throwable): Unit =
    underlying.reportFailure(t)
  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable =
    underlying.scheduleOnce(initialDelay, unit, r)
  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable =
    underlying.scheduleWithFixedDelay(initialDelay, delay, unit, r)
  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable =
    underlying.scheduleAtFixedRate(initialDelay, period, unit, r)
  override def currentTimeMillis(): Long =
    underlying.currentTimeMillis()
  override def withExecutionModel(em: ExecModel): TrampolineScheduler =
    new TrampolineScheduler(underlying, em)
}

object TrampolineScheduler {
  /** Builds a [[TrampolineScheduler]] instance.
    *
    * @param underlying is the [[monix.execution.Scheduler Scheduler]]
    *        to which the we defer to in case asynchronous or time-delayed
    *        execution is needed
    *
    * @define executionModel is the preferred [[ExecutionModel]],
    *         a guideline for run-loops and producers of data. Use
    *         [[monix.execution.ExecutionModel.Default ExecutionModel.Default]]
    *         for the default.
    */
  def apply(underlying: Scheduler, em: ExecModel): TrampolineScheduler =
    new TrampolineScheduler(underlying, em)
}