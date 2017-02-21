/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.types

import monix.types.utils._

/** `MonoidK` is a universal monoid which operates on kinds.
  *
  * To implement `MonoidK`:
  *
  *  - inherit from [[MonoidK.Type]] in derived type-classes
  *  - inherit from [[MonoidK.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scato
  * project by Aloïs Cochard and [[https://github.com/scalaz/scalaz/ Scalaz 8]]
  * and the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait MonoidK[F[_]] extends Serializable with SemigroupK.Type[F] {
  self: MonoidK.Instance[F] =>

  /** Given a type A, create an "empty" F[A] value. */
  def empty[A]: F[A]
}

object MonoidK {
  @inline def apply[F[_]](implicit F: MonoidK[F]): MonoidK[F] = F

  /** The `MonoidK.Type` should be inherited in type-classes that
    * are derived from [[MonoidK]].
    */
  trait Type[F[_]] extends SemigroupK.Type[F] {
    implicit def monoidK: MonoidK[F]
  }

  /** The `MonoidK.Instance` provides the means to combine
    * [[MonoidK]] instances with other type-classes.
    *
    * To be inherited by `MonoidK` instances.
    */
  trait Instance[F[_]] extends MonoidK[F] with Type[F]
    with SemigroupK.Instance[F] {

    override final def monoidK: MonoidK[F] = this
  }

  /** Laws for [[MonoidK]]. */
  trait Laws[F[_]] extends SemigroupK.Laws[F] with Type[F] {
    private def F = semigroupK
    private def M = monoidK

    def monoidKLeftIdentity[A](a: F[A]): IsEquiv[F[A]] =
      F.combineK(M.empty, a) <-> a

    def monoidKRightIdentity[A](a: F[A]): IsEquiv[F[A]] =
      F.combineK(a, M.empty) <-> a
  }
}
