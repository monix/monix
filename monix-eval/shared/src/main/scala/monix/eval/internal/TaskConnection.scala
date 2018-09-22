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

import cats.effect.CancelToken
import monix.execution.atomic.{Atomic, PaddingStrategy}
import monix.execution.{Cancelable, Scheduler}
import scala.annotation.tailrec

/**
  * INTERNAL API — Represents a composite of functions
  * (meant for cancellation) that are stacked.
  *
  * Implementation notes:
  *
  *  - `cancel()` is idempotent
  *  - all methods are thread-safe / atomic
  *
  * Used in the implementation of `cats.effect.Task`. Inspired by the
  * implementation of `StackedCancelable` from the Monix library.
  */
private[eval] sealed abstract class TaskConnection {
  /**
    * Cancels the unit of work represented by this reference.
    *
    * Guaranteed idempotency - calling it multiple times should have the
    * same side-effect as calling it only once. Implementations
    * of this method should also be thread-safe.
    */
  def cancel: CancelToken[Task]

  /**
    * @return true in case this cancelable hasn't been canceled,
    *         or false otherwise.
    */
  def isCanceled: Boolean

  /**
    * Pushes a cancelable reference on the stack, to be
    * popped or canceled later in FIFO order.
    *
    * The function needs a [[monix.execution.Scheduler Scheduler]]
    * to work because in case the connection was already cancelled,
    * then the given `token` needs to be cancelled as well.
    */
  def push(token: CancelToken[Task])(implicit s: Scheduler): Unit

  /**
    * Pushes multiple connections on the stack.
    *
    * The function needs a [[monix.execution.Scheduler Scheduler]]
    * to work because in case the connection was already cancelled,
    * then the given connections need to be cancelled as well.
    */
  def pushAll(seq: Seq[TaskConnection])(implicit s: Scheduler): Unit

  /**
    * Removes a cancelable reference from the stack in FIFO order.
    *
    * @return the cancelable reference that was removed.
    */
  def pop(): CancelToken[Task]

  /**
    * Tries to reset an `TaskConnection`, from a cancelled state,
    * back to a pristine state, but only if possible.
    *
    * Returns `true` on success, or `false` if there was a race
    * condition (i.e. the connection wasn't cancelled) or if
    * the type of the connection cannot be reactivated.
    */
  def tryReactivate(): Boolean

  /**
    * Transforms this `TaskConnection` into a
    * [[monix.execution.Cancelable Cancelable]] reference.
    */
  def toCancelable(implicit s: Scheduler): Cancelable
}

private[eval] object TaskConnection {
  /** Builder for [[TaskConnection]]. */
  def apply(): TaskConnection =
    new Impl

  /**
    * Reusable [[TaskConnection]] reference that cannot
    * be canceled.
    */
  val uncancelable: TaskConnection =
    new Uncancelable

  private final class Uncancelable extends TaskConnection {
    def cancel = Task.unit
    def isCanceled: Boolean = false
    def pop(): CancelToken[Task] = Task.unit
    def tryReactivate(): Boolean = true
    def push(token: CancelToken[Task])
      (implicit s: Scheduler): Unit = ()
    def pushAll(seq: Seq[TaskConnection])
      (implicit s: Scheduler): Unit = ()
    def toCancelable(implicit s: Scheduler): Cancelable =
      Cancelable.empty
  }

  private final class Impl extends TaskConnection { self =>
    private[this] val state =
      Atomic.withPadding(
        List.empty[CancelToken[Task]],
        PaddingStrategy.LeftRight128
      )

    val cancel = Task.suspend {
      state.getAndSet(null) match {
        case null | Nil =>
          Task.unit
        case list =>
          CancelUtils.cancelAll(list.iterator)
      }
    }

    def isCanceled: Boolean =
      state.get eq null

    @tailrec def push(cancelable: CancelToken[Task])
      (implicit s: Scheduler): Unit = {

      state.get match {
        case null =>
          // Cancelling
          cancelable.runAsyncAndForget(s)
        case list =>
          val update = cancelable :: list
          if (!state.compareAndSet(list, update)) push(cancelable)
      }
    }

    def pushAll(seq: Seq[TaskConnection])(implicit s: Scheduler): Unit =
      push(CancelUtils.cancelAll(seq.map(_.cancel) :_*))

    @tailrec def pop(): CancelToken[Task] =
      state.get match {
        case null | Nil => Task.unit
        case current @ (x :: xs) =>
          if (!state.compareAndSet(current, xs)) pop()
          else x
      }

    def tryReactivate(): Boolean =
      state.compareAndSet(null, Nil)

    def toCancelable(implicit s: Scheduler): Cancelable =
      new Cancelable {
        def cancel(): Unit =
          self.cancel.runAsyncAndForget(s)
      }
  }
}
