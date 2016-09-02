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

/** The `Applicative` type-class is a [[Functor]] that also adds the
  * capability of lifting a value in the context.
  *
  * Described in
  * [[http://www.soi.city.ac.uk/~ross/papers/Applicative.html
  * Applicative Programming with Effects]].
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
trait Applicative[F[_]] extends Serializable with Functor.Type[F] {
  def pure[A](a: A): F[A]
  def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z]
  def ap[A, B](ff: F[A => B])(fa: F[A]): F[B]
  def unit: F[Unit] = pure(())
}

object Applicative {
  @inline def apply[F[_]](implicit F: Applicative[F]): Applicative[F] = F

  /** The `Applicative.Type` should be inherited in type-classes that
    * are derived from [[Applicative]].
    */
  trait Type[F[_]] extends Functor.Type[F] {
    implicit def applicative: Applicative[F]
  }

  /** The `Applicative.Instance` provides the means to combine
    * [[Applicative]] instances with other type-classes.
    *
    * To be inherited by `Applicative` instances.
    */
  trait Instance[F[_]] extends Applicative[F] with Type[F]
    with Functor.Instance[F] {

    override final def applicative: Applicative[F] = this
  }

  /** Provides syntax for [[Applicative]]. */
  trait Syntax extends Serializable {
    implicit final def applicativeOps[F[_] : Applicative, A, B](ff: F[A => B]): OpsAP[F, A, B] =
      new OpsAP(ff)
  }

  /** Extension methods for [[Applicative]]. */
  final class OpsAP[F[_], A, B](self: F[A => B])(implicit F: Applicative[F])
    extends Serializable {

    /** Extension method for [[Applicative.ap]]. */
    def ap(fa: F[A]): F[B] = F.ap(self)(fa)
  }
}