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

package monix.eval.internal

import monix.execution.Callback
import monix.eval.Task
import monix.eval.Task.{ Async, Context }
import monix.execution.ExecutionModel
import monix.execution.ExecutionModel.{ AlwaysAsyncExecution, BatchedExecution, SynchronousExecution }

private[eval] object TaskExecuteWithModel {
  /**
    * Implementation for `Task.executeWithModel`
    */
  def apply[A](self: Task[A], em: ExecutionModel): Task[A] = {
    val start = (context: Context, cb: Callback[Throwable, A]) => {
      val context2 = context.withExecutionModel(em)
      val frame = context2.frameRef

      // Increment the frame index because we have a changed
      // execution model, or otherwise we risk not following it
      // for the next step in our evaluation
      val nextIndex = em match {
        case BatchedExecution(_) =>
          em.nextFrameIndex(frame())
        case AlwaysAsyncExecution | SynchronousExecution =>
          em.nextFrameIndex(0)
      }
      TaskRunLoop.startFull[A](self, context2, cb, null, null, null, nextIndex)
    }

    Async(
      start,
      trampolineBefore = false,
      trampolineAfter = true,
      restoreLocals = false
    )
  }
}
