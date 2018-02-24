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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.eval.Task.Context
import monix.execution.cancelables.SingleAssignCancelable

import scala.concurrent.duration.Duration

private[eval] object TaskSleep {
  def apply(timespan: Duration): Task[Unit] =
    Task.Async { (ctx, cb) =>
      val c = SingleAssignCancelable()
      ctx.connection.push(c)

      c := ctx.scheduler.scheduleOnce(
        timespan.length,
        timespan.unit,
        new SleepRunnable(ctx, cb))
    }

  private final class SleepRunnable(ctx: Context, cb: Callback[Unit])
    extends Runnable {

    def run(): Unit = {
      ctx.connection.pop()
      // We had an async boundary, as we must reset the frame
      ctx.frameRef.reset()
      cb.onSuccess(())
    }
  }
}
