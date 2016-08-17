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

/** A shim for a `MonoidK` type-class, to be supplied by / translated to
  * libraries such as Cats or Scalaz.
  *
  * `MonoidK` is a universal monoid which operates on kinds.
  */
trait MonoidK[F[_]] extends SemigroupK[F] {
  /**
    * Given a type A, create an "empty" F[A] value.
    */
  def empty[A]: F[A]
}

object MonoidK {
  @inline def apply[F[_]](implicit F: MonoidK[F]): MonoidK[F] = F
}