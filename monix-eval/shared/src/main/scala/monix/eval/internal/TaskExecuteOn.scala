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
import monix.eval.Task.{Async, Context}
import monix.execution.Scheduler

private[eval] object TaskExecuteOn {
  /**
    * Implementation for `Task.executeOn`.
    */
  def apply[A](source: Task[A], s: Scheduler, forceAsync: Boolean): Task[A] = {
    val start = (ctx: Context, cb: Callback[A]) => {
      val ctx2 = ctx.withScheduler(s)
      if (forceAsync)
        Task.unsafeStartAsync(source, ctx2, cb)
      else
        Task.unsafeStartNow(source, ctx2, cb)
    }
    Async(
      start,
      trampolineBefore = !forceAsync,
      trampolineAfter = true,
      restoreLocals = false)
  }
}
