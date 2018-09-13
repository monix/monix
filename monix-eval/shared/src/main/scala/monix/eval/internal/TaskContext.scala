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

package monix.eval
package internal

import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}
import monix.execution.{ExecutionModel, Scheduler}
import monix.execution.cancelables.StackedCancelable
import monix.execution.schedulers.TracingScheduler

/** Internal API — The context under which [[Task]] is supposed
  * to be executed, keeping connection details and run-loop settings
  * and state.
  *
  * This has been hidden in version 3.0.0-RC2, becoming an internal
  * implementation detail. Soon to be removed or changed completely.
  */
private[eval] final case class TaskContext(
  private val schedulerRef: Scheduler,
  options: TaskOptions,
  connection: StackedCancelable,
  frameRef: FrameIndexRef) {

  val scheduler: Scheduler =
    if (options.localContextPropagation)
      TracingScheduler(schedulerRef)
    else
      schedulerRef

  def shouldCancel: Boolean =
    options.autoCancelableRunLoops &&
      connection.isCanceled

  def executionModel: ExecutionModel =
    schedulerRef.executionModel

  def startFrame(currentFrame: FrameIndex = frameRef()): FrameIndex = {
    val em = schedulerRef.executionModel
    em match {
      case BatchedExecution(_) =>
        currentFrame
      case AlwaysAsyncExecution | SynchronousExecution =>
        em.nextFrameIndex(0)
    }
  }

  def withScheduler(s: Scheduler): TaskContext =
    new TaskContext(s, options, connection, frameRef)

  def withExecutionModel(em: ExecutionModel): TaskContext =
    new TaskContext(schedulerRef.withExecutionModel(em), options, connection, frameRef)

  def withOptions(opts: TaskOptions): TaskContext =
    new TaskContext(schedulerRef, opts, connection, frameRef)

  def withConnection(conn: StackedCancelable): TaskContext =
    new TaskContext(schedulerRef, options, conn, frameRef)
}

private[eval] object TaskContext {
  def apply(scheduler: Scheduler, options: TaskOptions): TaskContext =
    apply(scheduler, options, StackedCancelable())

  def apply(scheduler: Scheduler, options: TaskOptions, connection: StackedCancelable): TaskContext = {
    val em = scheduler.executionModel
    val frameRef = FrameIndexRef(em)
    new TaskContext(scheduler, options, connection, frameRef)
  }
}
