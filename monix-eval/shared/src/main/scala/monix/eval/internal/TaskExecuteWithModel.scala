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

import monix.eval.Task
import monix.execution.ExecutionModel
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}
import monix.execution.misc.NonFatal

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
        val frame = context2.frameRef
        streamErrors = false

        // Increment the frame index because we have a changed
        // execution model, or otherwise we risk not following it
        // for the next step in our evaluation
        val nextIndex = em match {
          case BatchedExecution(_) =>
            em.nextFrameIndex(frame())
          case AlwaysAsyncExecution | SynchronousExecution =>
            em.nextFrameIndex(0)
        }

        Task.internalStartTrampolineRunLoop[A](self, context2, cb, null, null, nextIndex)
      }
      catch {
        case NonFatal(ex) =>
          if (streamErrors) cb.onError(ex)
          else context.scheduler.reportFailure(ex)
      }
    }
}
