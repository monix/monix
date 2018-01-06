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
import monix.eval.Task.Options
import monix.execution.misc.NonFatal

private[eval] object TaskExecuteWithOptions {
  /**
    * Implementation for `Task.executeWithOptions`
    */
  def apply[A](self: Task[A], f: Options => Options): Task[A] =
    Task.unsafeCreate { (context, cb) =>
      implicit val s = context.scheduler
      var streamErrors = true
      try {
        val context2 = context.copy(options = f(context.options))
        streamErrors = false
        Task.unsafeStartTrampolined[A](self, context2, Callback.async(cb))
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) cb.asyncOnError(ex)
          else context.scheduler.reportFailure(ex)
      }
    }
}
