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

import simulacrum.typeclass
import scala.language.{higherKinds,implicitConversions}

/** Placeholder for a `Monad` type, to be provided by
  * libraries such as Cats or Scalaz.
  */
@typeclass trait Monad[F[_]] {
  def ap[A, B](fa: F[A])(f: F[A => B]): F[B] = flatMap(f)(map(fa))
  def map[A, B](fa: F[A])(f: (A) => B): F[B]
  def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B]
  def flatten[A](ffa: F[F[A]]): F[A]
  def point[A](a: A): F[A]
}
