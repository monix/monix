/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.eval.instances

import cats.{Monad, Monoid, Semigroup}

/** Given that `A` has a `cats.Semigroup` implementation, this
  * builds a `Semigroup[F[A]]` instance for any `F[_]` data type
  * that implements `cats.Monad`.
  *
  * Used for both [[monix.eval.Task Task]] and [[monix.eval.Coeval]].
  *
  * NOTE: nothing in this implementation is specific to Monix or to
  * `cats-effect`, but these instances are not provided by default
  * by Cats for any monad, probably because they aren't useful
  * for every monad.
  */
class CatsMonadToMonoid[F[_], A](implicit F: Monad[F], A: Monoid[A])
  extends CatsMonadToSemigroup[F, A] with Monoid[F[A]] {

  override def empty: F[A] =
    F.pure(A.empty)
}

/** Given that `A` has a `cats.Monoid` implementation, this builds
  * a `Semigroup[F[A]]` instance for any `F[_]` data type that
  * implements `cats.effect.Sync`.
  *
  * Used for both [[monix.eval.Task Task]] and [[monix.eval.Coeval]].
  *
  * NOTE: nothing in this implementation is specific to Monix or to
  * `cats-effect`, but these instances are not provided by default
  * by Cats for any monad, probably because they aren't useful
  * for every monad.
  */
class CatsMonadToSemigroup[F[_], A](implicit F: Monad[F], A: Semigroup[A])
  extends Semigroup[F[A]] {

  override def combine(x: F[A], y: F[A]): F[A] =
    F.flatMap(x)(a1 => F.map(y)(a2 => A.combine(a1, a2)))
}
