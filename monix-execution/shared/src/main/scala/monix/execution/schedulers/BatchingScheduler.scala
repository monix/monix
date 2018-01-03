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

import monix.execution.Scheduler
import scala.concurrent.ExecutionContext

/** Adds trampoline execution capabilities to
  * [[monix.execution.Scheduler schedulers]], when
  * inherited.
  *
  * When it receives [[TrampolinedRunnable]] instances, it
  * switches to a trampolined mode where all incoming
  * [[TrampolinedRunnable]] are executed on the current thread.
  *
  * This is useful for light-weight callbacks. The idea is
  * borrowed from the implementation of
  * `scala.concurrent.Future`. Currently used as an
  * optimization by `Task` in processing its internal callbacks.
  */
trait BatchingScheduler extends Scheduler { self =>
  protected def executeAsync(r: Runnable): Unit

  private[this] val trampoline =
    TrampolineExecutionContext(new ExecutionContext {
      def execute(runnable: Runnable): Unit =
        self.executeAsync(runnable)
      def reportFailure(cause: Throwable): Unit =
        self.reportFailure(cause)
    })

  override final def execute(runnable: Runnable): Unit =
    runnable match {
      case r: TrampolinedRunnable =>
        trampoline.execute(r)
      case _ =>
        // No local execution, forwards to underlying context
        executeAsync(runnable)
    }
}