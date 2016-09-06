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

/** A functor provides the `map` operation that allows lifting an `f`
  * function into the functor context and applying it.
  *
  * To implement `Functor`:
  *
  *  - inherit from [[Functor.Type]] in derived type-classes
  *  - inherit from [[Functor.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scado
  * project and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and
  * the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Functor[F[_]] extends Serializable {
  self: Functor.Instance[F] =>

  def map[A, B](fa: F[A])(f: A => B): F[B]
}

object Functor {
  @inline def apply[F[_]](implicit F: Functor[F]): Functor[F] = F

  /** The `Functor.Type` should be inherited in type-classes that
    * are derived from [[Functor]].
    */
  trait Type[F[_]] {
    implicit def functor: Functor[F]
  }

  /** The `Functor.Instance` provides the means to combine [[Functor]]
    * instances with other type-classes when implementing instances.
    *
    *  To be inherited by `Functor` instances.
    */
  trait Instance[F[_]] extends Functor[F] with Type[F] {
    override implicit final def functor: Functor[F] = this
  }

  /** Provides syntax for [[Functor]]. */
  trait Syntax extends Serializable {
    implicit final def functorOps[F[_] : Functor, A](fa: F[A]): Ops[F, A] =
      new Ops(fa)
  }

  /** Extension methods for [[Functor]]. */
  final class Ops[F[_], A](val self: F[A])(implicit val F: Functor[F])
    extends Serializable {

    /** Extension method for [[Functor.map]]. */
    def map[B](f: A => B): F[B] = macro monix.types.utils.Macros.functorMap
  }

  /** Laws for [[Functor]]. */
  trait Laws[F[_]] extends Type[F] {
    private def F = functor

    def covariantIdentity[A](fa: F[A]): IsEquiv[F[A]] =
      F.map(fa)(identity) <-> fa

    def covariantComposition[A, B, C](fa: F[A], f: A => B, g: B => C): IsEquiv[F[C]] =
      F.map(F.map(fa)(f))(g) <-> F.map(fa)(f andThen g)
  }
}


