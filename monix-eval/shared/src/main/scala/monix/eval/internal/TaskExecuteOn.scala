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

package monix.eval.internal

import monix.eval.Task.{ Async, Context }
import java.util.concurrent.RejectedExecutionException
import monix.execution.Callback
import monix.eval.Task
import monix.execution.Scheduler

private[eval] object TaskExecuteOn {
  /**
    * Implementation for `Task.executeOn`.
    */
  def apply[A](source: Task[A], s: Scheduler, forceAsync: Boolean): Task[A] = {
    val withTrampoline = !forceAsync
    val start =
      if (forceAsync) new AsyncRegister(source, s)
      else new TrampolinedStart(source, s)

    Async(
      start,
      trampolineBefore = withTrampoline,
      trampolineAfter = withTrampoline,
      restoreLocals = false
    )
  }

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is task that forks on evaluation
  private final class AsyncRegister[A](source: Task[A], s: Scheduler) extends ForkedRegister[A] {
    def apply(ctx: Context, cb: Callback[Throwable, A]): Unit = {
      val oldS = ctx.scheduler
      val ctx2 = ctx.withScheduler(s)

      try {
        Task.unsafeStartAsync(
          source,
          ctx2,
          new Callback[Throwable, A] with Runnable {
            private[this] var value: A = _
            private[this] var error: Throwable = _

            def onSuccess(value: A): Unit = {
              this.value = value
              oldS.execute(this)
            }

            def onError(ex: Throwable): Unit = {
              this.error = ex
              oldS.execute(this)
            }

            def run() = {
              if (error ne null) cb.onError(error)
              else cb.onSuccess(value)
            }
          }
        )
      } catch {
        case e: RejectedExecutionException =>
          Callback.signalErrorTrampolined(cb, e)
      }
    }
  }

  private final class TrampolinedStart[A](source: Task[A], s: Scheduler)
    extends ((Context, Callback[Throwable, A]) => Unit) {

    def apply(ctx: Context, cb: Callback[Throwable, A]): Unit = {
      val ctx2 = ctx.withScheduler(s)
      try {
        Task.unsafeStartNow(source, ctx2, cb)
      } catch {
        case e: RejectedExecutionException =>
          Callback.signalErrorTrampolined(cb, e)
      }
    }
  }
}
