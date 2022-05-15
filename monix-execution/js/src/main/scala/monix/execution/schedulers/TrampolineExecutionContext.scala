/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.execution.internal.Trampoline

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

/** A `scala.concurrentExecutionContext` implementation
  * that executes runnables immediately, on the current thread,
  * by means of a trampoline implementation.
  *
  * Can be used in some cases to keep the asynchronous execution
  * on the current thread, as an optimization, but be warned,
  * you have to know what you're doing.
  *
  * The `TrampolineExecutionContext` keeps a reference to another
  * `underlying` context, to which it defers for:
  *
  *  - reporting errors
  *  - deferring the rest of the queue in problematic situations
  *
  * Deferring the rest of the queue happens:
  *
  *  - in case we have a runnable throwing an exception, the rest
  *    of the tasks get re-scheduled for execution by using
  *    the `underlying` context
  *  - in case we have a runnable triggering a Scala `blocking`
  *    context, the rest of the tasks get re-scheduled for execution
  *    on the `underlying` context to prevent any deadlocks
  *
  * Thus this implementation is compatible with the
  * `scala.concurrent.BlockContext`, detecting `blocking` blocks and
  * reacting by forking the rest of the queue to prevent deadlocks.
  *
  * @param underlying is the `ExecutionContext` to which the it defers
  *        to in case real asynchronous is needed
  */
final class TrampolineExecutionContext private (underlying: ExecutionContext) extends ExecutionContextExecutor {

  private[this] val trampoline = new Trampoline

  override def execute(runnable: Runnable): Unit =
    trampoline.execute(runnable, underlying)
  override def reportFailure(t: Throwable): Unit =
    underlying.reportFailure(t)
}

object TrampolineExecutionContext {
  /** Builds a [[TrampolineExecutionContext]] instance.
    *
    * @param underlying is the `ExecutionContext` to which the
    *        it defers to in case asynchronous or time-delayed execution
    *        is needed
    */
  def apply(underlying: ExecutionContext): TrampolineExecutionContext =
    new TrampolineExecutionContext(underlying)

  /** [[TrampolineExecutionContext]] instance that executes everything
    * immediately, on the current thread.
    *
    * Implementation notes:
    *
    *  - if too many `blocking` operations are chained, at some point
    *    the implementation will trigger a stack overflow error
    *  - `reportError` re-throws the exception in the hope that it
    *    will get caught and reported by the underlying thread-pool,
    *    because there's nowhere it could report that error safely
    *    (i.e. `System.err` might be routed to `/dev/null` and we'd
    *    have no way to override it)
    */
  val immediate: TrampolineExecutionContext =
    TrampolineExecutionContext(new ExecutionContext {
      def execute(r: Runnable): Unit = r.run()
      def reportFailure(e: Throwable): Unit = throw e
    })
}
