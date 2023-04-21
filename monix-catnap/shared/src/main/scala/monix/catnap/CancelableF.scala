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

import cats.Applicative
import cats.effect.{ CancelToken, Sync }
import monix.catnap.cancelables.BooleanCancelableF
import monix.execution.annotations.UnsafeBecauseImpure
import monix.execution.exceptions.CompositeException
import scala.collection.mutable.ListBuffer

/** Represents a pure data structure that describes an effectful,
  * idempotent action that can be used to cancel async computations,
  * or to release resources.
  *
  * This is the pure, higher-kinded equivalent of
  * [[monix.execution.Cancelable]] and can be used in combination
  * with data types meant for managing effects, like
  * `Task`, `Coeval` or `cats.effect.IO`.
  *
  * Note: the `F` suffix comes from this data type being abstracted
  * over `F[_]`.
  */
trait CancelableF[F[_]] {
  def cancel: CancelToken[F]
}

object CancelableF {
  /**
    * Given a token that does not guarantee idempotency, wraps it
    * in a [[CancelableF]] value that guarantees the given token
    * will execute only once.
    */
  def apply[F[_]](token: F[Unit])(implicit F: Sync[F]): F[CancelableF[F]] =
    BooleanCancelableF(token).asInstanceOf[F[CancelableF[F]]]

  /**
    * Unsafe version of [[apply]].
    *
    * This function is unsafe because creating the returned
    * [[monix.catnap.cancelables.BooleanCancelableF BooleanCancelableF]]
    * allocates internal shared mutable state, thus breaking referential
    * transparency, which can catch users by surprise.
    */
  @UnsafeBecauseImpure
  def unsafeApply[F[_]](token: F[Unit])(implicit F: Sync[F]): CancelableF[F] =
    BooleanCancelableF.unsafeApply(token)

  /**
    * Wraps a cancellation token into a [[CancelableF]] instance.
    *
    * Compared with [[apply]] the returned value does not
    * guarantee idempotency.
    *
    * N.B. the returned result is as pure as the wrapped result.
    * Since we aren't allocating mutable internal state for creating
    * this value, we don't need to return the value in `F[_]`,
    * like in [[apply]].
    */
  def wrap[F[_]](token: CancelToken[F]): CancelableF[F] =
    new CancelableF[F] { def cancel = token }

  /**
    * Returns a dummy [[CancelableF]] that doesn't do anything.
    */
  def empty[F[_]](implicit F: Applicative[F]): CancelableF[F] =
    new CancelableF[F] { def cancel = F.unit }

  /** Builds a [[CancelableF]] reference from a sequence,
    * cancelling everything when `cancel` gets evaluated.
    */
  def collection[F[_]](refs: CancelableF[F]*)(implicit F: Sync[F]): CancelableF[F] =
    wrap[F](cancelAll(refs: _*))

  /** Given a collection of cancelables, creates a token that
    * on evaluation will cancel them all.
    *
    * This function collects non-fatal exceptions and throws them all
    * at the end as a composite, in a platform specific way:
    *
    *  - for the JVM "Suppressed Exceptions" are used
    *  - for JS they are wrapped in a `CompositeException`
    */
  def cancelAll[F[_]](seq: CancelableF[F]*)(implicit F: Sync[F]): CancelToken[F] = {

    if (seq.isEmpty) F.unit
    else
      F.defer {
        new CancelAllFrame[F](seq.map(_.cancel).iterator)(F).loop
      }
  }

  /** Given a collection of cancel tokens, creates a token that
    * on evaluation will cancel them all.
    *
    * This function collects non-fatal exceptions and throws them all
    * at the end as a composite, in a platform specific way:
    *
    *  - for the JVM "Suppressed Exceptions" are used
    *  - for JS they are wrapped in a `CompositeException`
    */
  def cancelAllTokens[F[_]](seq: CancelToken[F]*)(implicit F: Sync[F]): CancelToken[F] = {

    if (seq.isEmpty) F.unit
    else
      F.defer {
        new CancelAllFrame[F](seq.iterator)(F).loop
      }
  }

  /** Interface for cancelables that are empty or already canceled. */
  trait Empty[F[_]] extends CancelableF[F] with IsDummy[F]

  /** Marker for cancelables that are dummies that can be ignored. */
  trait IsDummy[F[_]] { self: CancelableF[F] => }

  // Optimization for `cancelAll`
  private final class CancelAllFrame[F[_]](cursor: Iterator[CancelToken[F]])(implicit F: Sync[F])
    extends (Either[Throwable, Unit] => F[Unit]) {

    private[this] val errors = ListBuffer.empty[Throwable]

    def loop: CancelToken[F] = {
      if (cursor.hasNext) {
        F.flatMap(F.attempt(cursor.next()))(this)
      } else {
        errors.toList match {
          case Nil =>
            F.unit
          case first :: Nil =>
            F.raiseError(first)
          case list =>
            F.raiseError(CompositeException(list))
        }
      }
    }

    def apply(r: Either[Throwable, Unit]): F[Unit] = {
      if (r.isLeft) r.swap.foreach { t =>
        errors += t
        ()
      }
      loop
    }
  }
}
