/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.execution

package object schedulers {
  /** Deprecated. Renamed to [[TrampolinedRunnable]]. */
  @deprecated("Renamed to `TrampolinedRunnable`", since="2.1.0")
  type LocalRunnable = TrampolinedRunnable

  /** Deprecated. Renamed to [[BatchingScheduler]]. */
  @deprecated("Renamed to `BatchingScheduler`", since="2.1.0")
  type LocalBatchingExecutor = BatchingScheduler

  /** Deprecated. Moved to [[monix.execution.ExecutionModel]]. */
  @deprecated("Moved to `monix.execution.ExecutionModel`", since="2.1.3")
  type ExecutionModel = monix.execution.ExecutionModel

  /** Deprecated. Moved to [[monix.execution.ExecutionModel]]. */
  @deprecated("Moved to `monix.execution.ExecutionModel`", since="2.1.3")
  object ExecutionModel extends Serializable {
    /** Deprecated. Moved to [[monix.execution.ExecutionModel.SynchronousExecution]]. */
    @deprecated("Moved to `monix.execution.ExecutionModel.SynchronousExecution`", since="2.1.3")
    def SynchronousExecution = monix.execution.ExecutionModel.SynchronousExecution

    /** Deprecated. Moved to [[monix.execution.ExecutionModel.AlwaysAsyncExecution]]. */
    @deprecated("Moved to `monix.execution.ExecutionModel.AlwaysAsyncExecution`", since="2.1.3")
    def AlwaysAsyncExecution = monix.execution.ExecutionModel.AlwaysAsyncExecution

    /** Deprecated. Moved to [[monix.execution.ExecutionModel.BatchedExecution]]. */
    @deprecated("Moved to `monix.execution.ExecutionModel.BatchedExecution`", since="2.1.3")
    type BatchedExecution = monix.execution.ExecutionModel.BatchedExecution

    /** Deprecated. Moved to [[monix.execution.ExecutionModel.BatchedExecution]]. */
    @deprecated("Moved to `monix.execution.ExecutionModel.BatchedExecution`", since="2.1.3")
    def BatchedExecution = monix.execution.ExecutionModel.BatchedExecution

    /** Deprecated. Moved to [[monix.execution.ExecutionModel.Default]]. */
    @deprecated("Moved to `monix.execution.ExecutionModel.Default`", since="2.1.3")
    def Default: monix.execution.ExecutionModel = monix.execution.ExecutionModel.Default
  }
}
