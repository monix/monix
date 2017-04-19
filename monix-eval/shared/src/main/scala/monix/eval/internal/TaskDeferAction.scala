/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.misc.NonFatal

private[eval] object TaskDeferAction {
  /** Implementation for `Task.deferAction`. */
  def apply[A](f: Scheduler => Task[A]): Task[A] =
    Task.unsafeCreate { (context, callback) =>
      implicit val ec = context.scheduler
      var streamErrors = true

      try {
        val fa = f(ec)
        streamErrors = false
        Task.unsafeStartTrampolined(fa, context, callback)
      }
      catch {
        case NonFatal(ex) =>
          if (streamErrors) callback.asyncOnError(ex)
          else ec.reportFailure(ex)
      }
    }
}
