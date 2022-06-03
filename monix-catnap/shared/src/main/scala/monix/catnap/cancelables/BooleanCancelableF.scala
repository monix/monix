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

package monix.catnap
package cancelables

import cats.Applicative
import cats.effect.{ CancelToken, Sync }
import monix.catnap.CancelableF
import monix.catnap.CancelableF.Empty
import monix.execution.annotations.UnsafeBecauseImpure
import monix.execution.atomic.Atomic

/**
  * Represents a [[CancelableF]] that can be queried for the
  * canceled status.
  *
  * @see [[monix.execution.cancelables.BooleanCancelable]] for
  *      the simpler, side-effecting version.
  */
trait BooleanCancelableF[F[_]] extends CancelableF[F] {
  /**
    * @return true in case this cancelable hasn't been canceled,
    *         or false otherwise.
    */
  def isCanceled: F[Boolean]
}

object BooleanCancelableF {
  /**
    * Builder for [[BooleanCancelableF]] that wraps and protects
    * the given cancellation token.
    *
    * The returned implementation guarantees idempotency by
    * ensuring that the given `token` will only be executed once,
    * the operation becoming a no-op on sub-sequent evaluations,
    * thus being memoized.
    *
    * @param token is a value that can be evaluated.
    */
  def apply[F[_]](token: CancelToken[F])(implicit F: Sync[F]): F[BooleanCancelableF[F]] =
    F.delay(unsafeApply[F](token))

  /**
    * Unsafe version of [[apply]].
    *
    * This function is unsafe because creating the returned
    * [[BooleanCancelableF]] allocates internal shared mutable
    * state, thus breaking referential transparency, which can
    * catch users by surprise.
    */
  @UnsafeBecauseImpure
  def unsafeApply[F[_]](token: CancelToken[F])(implicit F: Sync[F]): BooleanCancelableF[F] =
    new Impl[F](token)

  /**
    * Returns an instance of a [[BooleanCancelableF]] that's
    * already canceled.
    */
  def alreadyCanceled[F[_]](implicit F: Applicative[F]): BooleanCancelableF[F] with Empty[F] =
    new BooleanCancelableF[F] with Empty[F] {
      val isCanceled = F.pure(true)
      def cancel = F.unit
    }

  /**
    * Returns a [[BooleanCancelableF]] that can never be canceled.
    *
    * Useful as a low-overhead instance whose `isCanceled` value
    * is always `false`, thus similar in spirit with [[alreadyCanceled]].
    */
  def dummy[F[_]](implicit F: Applicative[F]): BooleanCancelableF[F] =
    new BooleanCancelableF[F] with Empty[F] {
      val isCanceled = F.pure(false)
      def cancel = F.unit
    }

  private final class Impl[F[_]](token: CancelToken[F])(implicit F: Sync[F]) extends BooleanCancelableF[F] {

    private[this] val canceled = Atomic(false)
    private[this] var ref = token

    def isCanceled =
      F.delay(canceled.get())

    def cancel: CancelToken[F] =
      F.defer {
        if (!canceled.getAndSet(true)) {
          val ref = this.ref
          this.ref = null.asInstanceOf[CancelToken[F]]
          ref
        } else {
          F.unit
        }
      }
  }
}
