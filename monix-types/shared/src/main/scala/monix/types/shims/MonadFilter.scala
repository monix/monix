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

/** A shim for a `MonadFilter` type-class, to be supplied by / translated to
  * libraries such as Cats or Scalaz.
  *
  * A Monad equipped with an additional method which allows us to
  * create an "Empty" value for the Monad (for whatever "empty" makes
  * sense for that particular monad). This is of particular interest to
  * us since it allows us to add a `filter` method to a Monad, which is
  * used when pattern matching or using guards in for comprehensions.
  */
trait MonadFilter[F[_]] extends Monad[F] {
  def empty[A]: F[A]

  def filter[A](fa: F[A])(f: A => Boolean): F[A]

  // flatMap(fa)(a => flatMap(f(a))(b => if (b) pure(a) else empty[A]))
  def filterM[A](fa: F[A])(f: A => F[Boolean]): F[A]
}

object MonadFilter {
  @inline def apply[F[_]](implicit F: MonadFilter[F]): MonadFilter[F] = F
}
