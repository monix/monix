/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.execution.ExecutionModel
import monix.execution.ExecutionModel.{ AlwaysAsyncExecution, BatchedExecution, SynchronousExecution }
import monix.execution.misc.ThreadLocal

/** Internal API — A reference that boxes a `FrameIndex` possibly
  * using a thread-local.
  *
  * This definition is of interest only when creating
  * tasks with `Task.unsafeCreate`, which exposes internals,
  * is considered unsafe to use and is now deprecated.
  *
  * In case the [[Task]] is executed with
  * [[monix.execution.ExecutionModel.BatchedExecution BatchedExecution]],
  * this class boxes a `FrameIndex` in order to transport it over
  * light async boundaries, possibly using a
  * [[monix.execution.misc.ThreadLocal ThreadLocal]], since this
  * index is not supposed to survive when threads get forked.
  *
  * The `FrameIndex` is a counter that increments whenever a
  * `flatMap` operation is evaluated. And with `BatchedExecution`,
  * whenever that counter exceeds the specified threshold, an
  * asynchronous boundary is automatically inserted. However this
  * capability doesn't blend well with light asynchronous
  * boundaries, for example `Async` tasks that never fork logical threads or
  * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]]
  * instances executed by capable schedulers. This is why
  * [[FrameIndexRef]] is part of the `Context` of execution for
  * [[Task]], available for asynchronous tasks that get created with
  * `Task.unsafeCreate` (which is now deprecated).
  *
  * Note that in case the execution model is not
  * [[monix.execution.ExecutionModel.BatchedExecution BatchedExecution]]
  * then this reference is just a dummy, since there's no point in
  * keeping a counter around, plus setting and fetching from a
  * `ThreadLocal` can be quite expensive.
  */
private[eval] sealed abstract class FrameIndexRef {
  /** Returns the current `FrameIndex`. */
  def apply(): FrameIndex

  /** Stores a new `FrameIndex`. */
  def `:=`(update: FrameIndex): Unit

  /** Resets the stored `FrameIndex` to 1, which is the
    * default value that should be used after an asynchronous
    * boundary happened.
    */
  def reset(): Unit
}

private[eval] object FrameIndexRef {
  /** Builds a [[FrameIndexRef]]. */
  def apply(em: ExecutionModel): FrameIndexRef =
    em match {
      case AlwaysAsyncExecution | SynchronousExecution => Dummy
      case BatchedExecution(_) => new Local
    }

  // Keeps our frame index in a thread-local
  private final class Local extends FrameIndexRef {
    private[this] val local = ThreadLocal(1)
    def apply(): FrameIndex = local.get()
    def `:=`(update: FrameIndex): Unit = local.set(update)
    def reset(): Unit = local.reset()
  }

  // Dummy implementation that doesn't do anything
  private object Dummy extends FrameIndexRef {
    def apply(): FrameIndex = 1
    def `:=`(update: FrameIndex): Unit = ()
    def reset(): Unit = ()
  }
}
