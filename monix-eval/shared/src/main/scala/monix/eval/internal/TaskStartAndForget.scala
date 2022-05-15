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

package monix.eval
package internal

import monix.execution.Callback
import monix.eval.Task.Context

private[eval] object TaskStartAndForget {
  /**
    * Implementation for `Task.startAndForget`.
    */
  def apply[A](fa: Task[A]): Task[Unit] = {
    val start = (ctx: Context, cb: Callback[Throwable, Unit]) => {
      implicit val sc = ctx.scheduler
      // It needs its own context, its own cancelable
      val ctx2 = Task.Context(sc, ctx.options)
      // Starting actual execution of our newly created task forcing new async boundary
      Task.unsafeStartEnsureAsync(fa, ctx2, Callback.empty)
      cb.onSuccess(())
    }
    Task.Async[Unit](start, trampolineBefore = false, trampolineAfter = true)
  }
}
