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

/** The `MonadFilter` type-class is equipped with an additional
  * operation which allows us to create an "Empty" value for the Monad
  * (for whatever "empty" makes sense for that particular monad). This
  * is of particular interest to us since it allows us to add a
  * `filter` method to a Monad, which is used when pattern matching or
  * using guards in for comprehensions.
  *
  * To implement `MonadFilter`:
  *
  *  - inherit from [[MonadFilter.Type]] in derived type-classes
  *  - inherit from [[MonadFilter.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scato
  * project by Aloïs Cochard and [[https://github.com/scalaz/scalaz/ Scalaz 8]]
  * and the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait MonadFilter[F[_]] extends Serializable with Monad.Type[F] {
  self: MonadFilter.Instance[F] =>

  def empty[A]: F[A]
  def filter[A](fa: F[A])(f: A => Boolean): F[A]
}

object MonadFilter {
  @inline def apply[F[_]](implicit F: MonadFilter[F]): MonadFilter[F] = F

  /** The `MonadFilter.Type` should be inherited in type-classes that
    * are derived from [[MonadFilter]].
    */
  trait Type[F[_]] extends Monad.Type[F] {
    implicit def monadFilter: MonadFilter[F]
  }

  /** The `MonadFilter.Instance` provides the means to combine
    * [[MonadFilter]] instances with other type-classes.
    *
    * To be inherited by `MonadFilter` instances.
    */
  trait Instance[F[_]] extends MonadFilter[F] with Type[F] with Monad.Instance[F] {
    override final def monadFilter: MonadFilter[F] = this
  }

  /** Provides syntax for [[MonadFilter]]. */
  trait Syntax extends Serializable {
    implicit final def monadFilterOps[F[_] : MonadFilter, A](fa: F[A]): Ops[F, A] =
      new Ops(fa)
  }

  /** Extension methods for [[MonadFilter]]. */
  final class Ops[F[_], A](self: F[A])(implicit F: MonadFilter[F])
    extends Serializable {

    /** Extension method for [[MonadFilter.filter]]. */
    def filter(f: A => Boolean): F[A] = macro Macros.monadFilter
  }

  /** Laws for [[MonadFilter]]. */
  trait Laws[F[_]] extends Monad.Laws[F] with Type[F] {
    private def MF = monadFilter
    private def M = monad
    private def A = applicative

    def monadFilterLeftEmpty[A, B](f: A => F[B]): IsEquiv[F[B]] =
      M.flatMap(MF.empty[A])(f) <-> MF.empty[B]

    def monadFilterRightEmpty[A, B](fa: F[A]): IsEquiv[F[B]] =
      M.flatMap(fa)(_ => MF.empty[B]) <-> MF.empty[B]

    def monadFilterConsistency[A, B](fa: F[A], f: A => Boolean): IsEquiv[F[A]] =
      MF.filter(fa)(f) <-> M.flatMap(fa)(a => if (f(a)) A.pure(a) else MF.empty)
  }
}
