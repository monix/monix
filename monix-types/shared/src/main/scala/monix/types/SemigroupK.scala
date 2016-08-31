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
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  * 
  * To implement it in instances, inherit from [[SemigroupKClass]].
  * 
  * Credit should be given where it is due.The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait SemigroupK[F[_]] extends Serializable { self =>
  /** Combine two F[A] values. */
  def combineK[A](x: F[A], y: F[A]): F[A]
}

object SemigroupK {
  @inline def apply[F[_]](implicit F: SemigroupK[F]): SemigroupK[F] = F
}

/** The `SemigroupKClass` provides the means to combine [[SemigroupK]]
  * instances with other type-classes.
  * 
  * To be inherited by `SemigroupK` instances.
  */
trait SemigroupKClass[F[_]] extends SemigroupK[F] {
  final def semigroupK: SemigroupK[F] = this
}

