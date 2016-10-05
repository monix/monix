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
  def apply[A](self: Task[A], f: ExecutionModel => ExecutionModel): Task[A] =
    Task.unsafeCreate { (s, conn, frameRef, cb) =>
      var streamErrors = true
      try {
        val em = f(s.executionModel)
        implicit val s2 = s.withExecutionModel(em)
        streamErrors = false
        Task.unsafeStartTrampolined[A](self, s2, conn, frameRef, Callback.async(cb))
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) cb.onError(ex)
          else s.reportFailure(ex)
      }
    }
}
