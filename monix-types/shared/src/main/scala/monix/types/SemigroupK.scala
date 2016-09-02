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

/** `SemigroupK` is a universal semigroup which operates on kinds.
  *
  * To implement `SemigroupK`:
  *
  *  - inherit from [[SemigroupK.Type]] in derived type-classes
  *  - inherit from [[SemigroupK.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scado
  * project and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and
  * the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait SemigroupK[F[_]] extends Serializable {
  self: SemigroupK.Instance[F] =>

  /** Combine two F[A] values. */
  def combineK[A](x: F[A], y: F[A]): F[A]
}

object SemigroupK {
  @inline def apply[F[_]](implicit F: SemigroupK[F]): SemigroupK[F] = F

  /** The `SemigroupK.Type` should be inherited in type-classes that
    * are derived from [[SemigroupK]].
    */
  trait Type[F[_]] {
    implicit def semigroupK: SemigroupK[F]
  }

  /** The `SemigroupK.Instance` provides the means to combine [[SemigroupK]]
    * instances with other type-classes.
    *
    * To be inherited by `SemigroupK` instances.
    */
  trait Instance[F[_]] extends SemigroupK[F] with Type[F] {
    override final def semigroupK: SemigroupK[F] = this
  }
}

