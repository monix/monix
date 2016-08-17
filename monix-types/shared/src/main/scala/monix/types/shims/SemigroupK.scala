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

package monix.types.shims

/** A shim for a `SemigroupK` type-class, to be supplied by / translated to
  * libraries such as Cats or Scalaz.
  *
  * `SemigroupK` is a universal semigroup which operates on kinds.
  */
trait SemigroupK[F[_]] extends Any with Serializable { self =>
  /**
    * Combine two F[A] values.
    */
  def combineK[A](x: F[A], y: F[A]): F[A]
}

object SemigroupK {
  @inline def apply[F[_]](implicit F: SemigroupK[F]): SemigroupK[F] = F
}