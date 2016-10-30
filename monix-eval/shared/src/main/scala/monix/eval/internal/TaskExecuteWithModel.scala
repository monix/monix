/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
import monix.execution.schedulers.ExecutionModel
import scala.util.control.NonFatal

private[monix] object TaskExecuteWithModel {
  /**
    * Implementation for `Task.executeWithModel`
    */
  def apply[A](self: Task[A], em: ExecutionModel): Task[A] =
    Task.unsafeCreate { (context, cb) =>
      var streamErrors = true
      try {
        implicit val s2 = context.scheduler.withExecutionModel(em)
        val context2 = context.copy(scheduler = s2)
        streamErrors = false
        Task.unsafeStartTrampolined[A](self, context2, Callback.async(cb))
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) cb.onError(ex)
          else context.scheduler.reportFailure(ex)
      }
    }
}
