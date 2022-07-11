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

import java.util.concurrent.RejectedExecutionException

import monix.eval.Task.{ Async, Context }
import monix.execution.Callback
import monix.eval.Task

import scala.concurrent.duration.Duration

private[eval] object TaskSleep {
  /** Implementation for `Task.sleep`. */
  def apply(timespan: Duration): Task[Unit] =
    Async(
      new Register(timespan),
      trampolineBefore = false,
      trampolineAfter = false
    )

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is a task that forks on evaluation.
  //
  // N.B. the contract is that the injected callback gets called after
  // a full async boundary!
  private final class Register(timespan: Duration) extends ForkedRegister[Unit] {
    def apply(ctx: Context, cb: Callback[Throwable, Unit]): Unit = {
      implicit val s = ctx.scheduler
      val c = TaskConnectionRef()
      ctx.connection.push(c.cancel)

      try {
        c := ctx.scheduler.scheduleOnce(
          timespan.length,
          timespan.unit,
          new SleepRunnable(ctx, cb)
        )
      } catch {
        case e: RejectedExecutionException =>
          Callback.signalErrorTrampolined(cb, e)
      }
    }
  }

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is a task that forks on evaluation.
  //
  // N.B. the contract is that the injected callback gets called after
  // a full async boundary!
  private final class SleepRunnable(ctx: Context, cb: Callback[Throwable, Unit]) extends Runnable {
    def run(): Unit = {
      ctx.connection.pop()
      // We had an async boundary, as we must reset the frame
      ctx.frameRef.reset()
      cb.onSuccess(())
    }
  }
}
