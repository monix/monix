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

package monix.eval
package internal

import cats.effect.CancelToken
import monix.catnap.CancelableF
import monix.execution.atomic.{ Atomic, PaddingStrategy }
import monix.execution.{ Cancelable, Scheduler }

import scala.annotation.tailrec
import scala.concurrent.Promise

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
private[eval] sealed abstract class TaskConnection extends CancelableF[Task] {
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
    * Pushes a cancelable token on the stack, to be
    * popped or canceled later in LIFO order.
    *
    * The function needs a [[monix.execution.Scheduler Scheduler]]
    * to work because in case the connection was already cancelled,
    * then the given `token` needs to be cancelled as well.
    */
  def push(token: CancelToken[Task])(implicit s: Scheduler): Unit

  /**
    * Pushes a [[monix.execution.Cancelable]] on the stack, to be
    * popped or canceled later in LIFO order.
    *
    * The function needs a [[monix.execution.Scheduler Scheduler]]
    * to work because in case the connection was already cancelled,
    * then the given `token` needs to be cancelled as well.
    */
  def push(cancelable: Cancelable)(implicit s: Scheduler): Unit

  /**
    * Pushes a [[monix.catnap.CancelableF]] on the stack, to be
    * popped or canceled later in LIFO order.
    *
    * The function needs a [[monix.execution.Scheduler Scheduler]]
    * to work because in case the connection was already cancelled,
    * then the given `token` needs to be cancelled as well.
    */
  def push(connection: CancelableF[Task])(implicit s: Scheduler): Unit

  /**
    * Pushes multiple connections on the stack.
    *
    * The function needs a [[monix.execution.Scheduler Scheduler]]
    * to work because in case the connection was already cancelled,
    * then the given connections need to be cancelled as well.
    */
  def pushConnections(seq: CancelableF[Task]*)(implicit s: Scheduler): Unit

  /**
    * Removes a cancelable reference from the stack in LIFO order.
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
    def push(token: CancelToken[Task])(implicit s: Scheduler): Unit = ()
    def push(cancelable: Cancelable)(implicit s: Scheduler): Unit = ()
    def push(connection: CancelableF[Task])(implicit s: Scheduler): Unit = ()
    def pushConnections(seq: CancelableF[Task]*)(implicit s: Scheduler): Unit = ()
    def toCancelable(implicit s: Scheduler): Cancelable =
      Cancelable.empty
  }

  private final class Impl extends TaskConnection { self =>
    private[this] val state =
      Atomic.withPadding(
        (List.empty[AnyRef], Promise[Unit]()),
        PaddingStrategy.LeftRight128
      )

    val cancel = Task.suspend {
      state.transformAndExtract {
        case (Nil, p) =>
          (Task[Unit] { p.success(()): Unit }, (null, p))
        case (null, p) =>
          (TaskFromFuture.strict(p.future), (null, p))
        case (list, p) =>
          val task = UnsafeCancelUtils
            .cancelAllUnsafe(list)
            .redeemWith[Unit](
              ex => Task(p.success(())).flatMap(_ => Task.raiseError(ex)),
              _ => Task { p.success(()): Unit }
            )
          (task, (null, p))
      }
    }

    def isCanceled: Boolean =
      state.get()._1 eq null

    def push(token: CancelToken[Task])(implicit s: Scheduler): Unit =
      pushAny(token)
    def push(cancelable: Cancelable)(implicit s: Scheduler): Unit =
      pushAny(cancelable)
    def push(connection: CancelableF[Task])(implicit s: Scheduler): Unit =
      pushAny(connection)

    @tailrec
    private def pushAny(cancelable: AnyRef)(implicit s: Scheduler): Unit = {
      state.get() match {
        case (null, _) =>
          UnsafeCancelUtils.triggerCancel(cancelable)
        case current @ (list, p) =>
          val update = cancelable :: list
          if (!state.compareAndSet(current, (update, p))) {
            // $COVERAGE-OFF$
            pushAny(cancelable)
            // $COVERAGE-ON$
          }
      }
    }

    def pushConnections(seq: CancelableF[Task]*)(implicit s: Scheduler): Unit =
      push(UnsafeCancelUtils.cancelAllUnsafe(seq))

    @tailrec def pop(): CancelToken[Task] =
      state.get() match {
        case (null, _) | (Nil, _) => Task.unit
        case current @ (x :: xs, p) =>
          if (state.compareAndSet(current, (xs, p)))
            UnsafeCancelUtils.getToken(x)
          else {
            // $COVERAGE-OFF$
            pop()
            // $COVERAGE-ON$
          }
      }

    def tryReactivate(): Boolean = {
      state.transformAndExtract {
        case (null, _) =>
          (true, (Nil, Promise[Unit]()))
        case notCanceled =>
          (false, notCanceled)
      }
    }

    def toCancelable(implicit s: Scheduler): Cancelable =
      new Cancelable {
        def cancel(): Unit =
          self.cancel.runAsyncAndForget(s)
      }
  }
}
