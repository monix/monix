/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.eval.internal

import java.util.concurrent.RejectedExecutionException
import monix.eval.Task
import monix.eval.Task.{ Async, Context }
import monix.execution.schedulers.TracingScheduler
import monix.execution.{ Callback, Scheduler }
import scala.concurrent.ExecutionContext

private[eval] object TaskShift {
  /**
    * Implementation for `Task.shift`
    */
  def apply(ec: ExecutionContext): Task[Unit] = {
    Async(
      new Register(ec),
      trampolineBefore = false,
      trampolineAfter = false,
      restoreLocals = false
    )
  }

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is a task that forks on evaluation.
  //
  // N.B. the contract is that the injected callback gets called after
  // a full async boundary!
  private final class Register(ec: ExecutionContext) extends ForkedRegister[Unit] {
    def apply(context: Context, cb: Callback[Throwable, Unit]): Unit = {
      val ec2 =
        if (ec eq null) {
          context.scheduler
        } else if (context.options.localContextPropagation) {
          ec match {
            case sc: Scheduler if sc.features.contains(Scheduler.TRACING) =>
              sc
            case _ =>
              TracingScheduler(ec)
          }
        } else {
          ec
        }

      try {
        ec2.execute(() => {
          context.frameRef.reset()
          cb.onSuccess(())
        })
      } catch {
        case e: RejectedExecutionException =>
          Callback.signalErrorTrampolined(cb, e)
      }
    }
  }
}
