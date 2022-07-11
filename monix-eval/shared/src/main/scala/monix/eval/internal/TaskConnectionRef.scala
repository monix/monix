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

import cats.effect.CancelToken
import monix.catnap.CancelableF
import monix.execution.{ Cancelable, Scheduler }
import monix.execution.atomic.Atomic
import scala.annotation.tailrec

private[eval] final class TaskConnectionRef extends CancelableF[Task] {
  import TaskConnectionRef._

  @throws(classOf[IllegalStateException])
  def `:=`(token: CancelToken[Task])(implicit s: Scheduler): Unit =
    unsafeSet(token)

  @throws(classOf[IllegalStateException])
  def `:=`(cancelable: Cancelable)(implicit s: Scheduler): Unit =
    unsafeSet(cancelable)

  @throws(classOf[IllegalStateException])
  def `:=`(conn: CancelableF[Task])(implicit s: Scheduler): Unit =
    unsafeSet(conn.cancel)

  @tailrec
  private def unsafeSet(ref: AnyRef /* CancelToken[Task] | CancelableF[Task] | Cancelable */ )(
    implicit s: Scheduler
  ): Unit = {

    if (!state.compareAndSet(Empty, IsActive(ref))) {
      state.get() match {
        case IsEmptyCanceled =>
          state.getAndSet(IsCanceled) match {
            case IsEmptyCanceled =>
              UnsafeCancelUtils.triggerCancel(ref)
            case _ =>
              UnsafeCancelUtils.triggerCancel(ref)
              raiseError()
          }
        case IsCanceled | IsActive(_) =>
          UnsafeCancelUtils.triggerCancel(ref)
          raiseError()
        case Empty =>
          // $COVERAGE-OFF$
          unsafeSet(ref)
        // $COVERAGE-ON$
      }
    }
  }

  val cancel: CancelToken[Task] = {
    @tailrec def loop(): CancelToken[Task] =
      state.get() match {
        case IsCanceled | IsEmptyCanceled =>
          Task.unit
        case IsActive(task) =>
          state.set(IsCanceled)
          UnsafeCancelUtils.unsafeCancel(task)
        case Empty =>
          if (state.compareAndSet(Empty, IsEmptyCanceled)) {
            Task.unit
          } else {
            // $COVERAGE-OFF$
            loop() // retry
            // $COVERAGE-ON$
          }
      }
    Task.suspend(loop())
  }

  private def raiseError(): Nothing = {
    throw new IllegalStateException(
      "Cannot assign to SingleAssignmentCancelable, " +
        "as it was already assigned once"
    )
  }

  private[this] val state = Atomic(Empty: State)
}

private[eval] object TaskConnectionRef {
  /**
    * Returns a new `TaskForwardConnection` reference.
    */
  def apply(): TaskConnectionRef = new TaskConnectionRef()

  private sealed trait State
  private case object Empty extends State
  private final case class IsActive(token: AnyRef /* CancelToken[Task] | CancelableF[Task] | Cancelable */ )
    extends State
  private case object IsCanceled extends State
  private case object IsEmptyCanceled extends State
}
