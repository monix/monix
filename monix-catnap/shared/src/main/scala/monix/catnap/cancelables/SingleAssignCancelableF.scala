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

package monix.catnap.cancelables

import cats.effect.Sync
import monix.catnap.CancelableF
import monix.catnap.CancelableF.CancelToken
import monix.execution.annotations.UnsafeBecauseImpure
import monix.execution.atomic.Atomic
import scala.annotation.tailrec

/** Represents a [[monix.catnap.CancelableF]] that can be assigned only
  * once to another cancelable reference.
  *
  * If the assignment happens after this cancelable has been canceled, then on
  * assignment the reference will get canceled too. If the assignment already
  * happened, then a second assignment will raise an error.
  *
  * Useful in case you need a forward reference.
  */
final class SingleAssignCancelableF[F[_]] private (extra: CancelableF[F])(implicit F: Sync[F])
  extends AssignableCancelableF.Bool[F] {

  import SingleAssignCancelableF._
  private[this] val state = Atomic(Empty: State[F])

  val isCanceled: F[Boolean] =
    F.delay(state.get() match {
      case IsCanceled | IsEmptyCanceled => true
      case _ => false
    })

  val cancel: CancelToken[F] = {
    @tailrec def loop(): F[Unit] =
      state.get() match {
        case IsCanceled | IsEmptyCanceled => F.unit
        case current @ IsActive(s) =>
          if (state.compareAndSet(current, IsCanceled))
            CancelableF.cancelAllTokens(s.cancel, extra.cancel)
          else
            loop()
        case Empty =>
          if (state.compareAndSet(Empty, IsEmptyCanceled))
            extra.cancel
          else
            loop()
      }
    F.defer(loop())
  }

  def set(ref: CancelableF[F]): F[Unit] =
    F.defer(unsafeLoop(ref))

  @tailrec
  private def unsafeLoop(ref: CancelableF[F]): F[Unit] = {
    if (state.compareAndSet(Empty, IsActive(ref)))
      F.unit
    else
      state.get() match {
        case IsEmptyCanceled =>
          if (state.compareAndSet(IsEmptyCanceled, IsCanceled))
            ref.cancel
          else
            unsafeLoop(ref)

        case IsCanceled | IsActive(_) =>
          F.flatMap(ref.cancel)(_ => raiseError)
        case Empty =>
          unsafeLoop(ref)
      }
  }

  private def raiseError: F[Unit] = F.raiseError {
    new IllegalStateException(
      "Cannot assign to SingleAssignmentCancelableF " +
        "as it was already assigned once")
  }
}

object SingleAssignCancelableF {
  /**
    * Builder for [[SingleAssignCancelableF]].
    */
  def apply[F[_]](implicit F: Sync[F]): F[SingleAssignCancelableF[F]] =
    plusOne(CancelableF.empty)

  /**
    * Builder for [[SingleAssignCancelableF]] that takes an extra reference,
    * to be canceled on [[SingleAssignCancelableF.cancel cancel]]
    * along with whatever underlying reference we have.
    */
  def plusOne[F[_]](extra: CancelableF[F])(implicit F: Sync[F]): F[SingleAssignCancelableF[F]] =
    F.delay(unsafePlusOne(extra))

  /**
    * Unsafe version of [[apply]]
    *
    * Breaks referential transparency. Prefer the safe version.
    */
  @UnsafeBecauseImpure
  def unsafeApply[F[_]](implicit F: Sync[F]): SingleAssignCancelableF[F] =
    unsafePlusOne(CancelableF.empty)

  /**
    * Unsafe version of [[plusOne]]
    *
    * Breaks referential transparency. Prefer the safe version.
    */
  @UnsafeBecauseImpure
  def unsafePlusOne[F[_]](extra: CancelableF[F])(implicit F: Sync[F]): SingleAssignCancelableF[F] =
    new SingleAssignCancelableF[F](extra)

  private sealed trait State[+F[_]]
  private case object Empty extends State[Nothing]
  private case class IsActive[F[_]](s: CancelableF[F]) extends State[F]
  private case object IsCanceled extends State[Nothing]
  private case object IsEmptyCanceled extends State[Nothing]
}
