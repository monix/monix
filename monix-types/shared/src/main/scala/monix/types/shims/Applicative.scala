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

/** A shim for the Applicative Functor type-class,
  * to be supplied by libraries such as Cats or Scalaz.
  *
  * Described in [[http://www.soi.city.ac.uk/~ross/papers/Applicative.html Applicative Programming with Effects]].
  *
  * The [[Functor]] allows mapping of a pure function to a value, the `Applicative`
  * also adds the capability of lifting a value in the context.
  */
trait Applicative[F[_]] extends Functor[F] {
  def pure[A](a: A): F[A]
  def pureEval[A](a: => A): F[A]
  def ap[A, B](fa: F[A])(ff: F[A => B]): F[B]
  def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z]
}

object Applicative {
  @inline def apply[F[_]](implicit F: Applicative[F]): Applicative[F] = F
}
