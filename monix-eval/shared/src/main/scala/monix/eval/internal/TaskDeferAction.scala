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
import monix.execution.Scheduler
import scala.util.control.NonFatal

private[eval] object TaskDeferAction {
  /** Implementation for `Task.deferAction`. */
  def apply[A](f: Scheduler => Task[A]): Task[A] = {
    val start = (context: Context, callback: Callback[Throwable, A]) => {
      implicit val ec = context.scheduler
      var streamErrors = true

      try {
        val fa = f(ec)
        streamErrors = false
        Task.unsafeStartNow(fa, context, callback)
      } catch {
        case ex if NonFatal(ex) =>
          if (streamErrors)
            callback.onError(ex)
          else {
            // $COVERAGE-OFF$
            ec.reportFailure(ex)
            // $COVERAGE-ON$
          }
      }
    }

    Task.Async(
      start,
      trampolineBefore = true,
      trampolineAfter = true,
      restoreLocals = false
    )
  }
}
