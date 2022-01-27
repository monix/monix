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

package monix.catnap
package cancelables

import cats.Applicative
import monix.catnap.CancelableF
import monix.catnap.CancelableF.Empty

/** Represents a class of cancelable references that can hold
  * an internal reference to another cancelable (and thus has to
  * support the assignment operator).
  *
  * On assignment, if this cancelable is already
  * canceled, then no assignment should happen and the update
  * reference should be canceled as well.
  */
trait AssignableCancelableF[F[_]] extends CancelableF[F] {
  /**
    * Sets the underlying reference to the given [[CancelableF]] reference.
    *
    * Contract:
    *
    *  - the given reference gets canceled in case the assignable was
    *    already canceled
    *  - this operation might throw an error if the contract of the
    *    implementation doesn't allow for calling `set` multiple times
    *    (e.g. [[SingleAssignCancelableF]])
    */
  def set(cancelable: CancelableF[F]): F[Unit]
}

object AssignableCancelableF {
  /**
    * Represents [[AssignableCancelableF]] instances that are also
    * [[BooleanCancelableF]].
    */
  trait Bool[F[_]] extends AssignableCancelableF[F] with BooleanCancelableF[F]

  /**
    * Interface for [[AssignableCancelableF]] types that can be
    * assigned multiple times.
    */
  trait Multi[F[_]] extends Bool[F]

  /**
    * Builds an [[AssignableCancelableF]] instance that's already canceled.
    */
  def alreadyCanceled[F[_]](implicit F: Applicative[F]): Bool[F] with Empty[F] =
    new Bool[F] with Empty[F] {
      def set(ref: CancelableF[F]): F[Unit] = ref.cancel
      def isCanceled: F[Boolean] = F.pure(true)
      def cancel: CancelableF.CancelToken[F] = F.unit
    }

  /**
    * Represents an [[AssignableCancelableF]] with no internal state and that
    * doesn't do anything, either on assignment or on cancelation.
    *
    * It's a no-op.
    */
  def dummy[F[_]](implicit F: Applicative[F]): Multi[F] =
    new Multi[F] {
      def set(ref: CancelableF[F]): F[Unit] = F.unit
      def isCanceled: F[Boolean] = F.pure(false)
      def cancel: CancelableF.CancelToken[F] = F.unit
    }
}
