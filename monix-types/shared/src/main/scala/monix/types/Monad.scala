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

/** The `Monad` type-class is a structure that represents
  * computations defined as sequences of steps: : a type with
  * a monad structure defines what it means to chain operations
  * together, or nest functions of that type.
  *
  * See:
  * [[http://homepages.inf.ed.ac.uk/wadler/papers/marktoberdorf/baastad.pdf
  * Monads for functional programming]]
  *
  * To implement `Monad`:
  *
  *  - inherit from [[Monad.Type]] in derived type-classes
  *  - inherit from [[Monad.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scado
  * project and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and
  * the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Monad[F[_]] extends Serializable with Applicative.Type[F] {
  self: Monad.Instance[F] =>

  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def flatten[A](ffa: F[F[A]]): F[A] =
    flatMap(ffa)(x => x)
}

object Monad {
  @inline def apply[F[_]](implicit F: Monad[F]): Monad[F] = F

  /** The `Monad.Type` should be inherited in type-classes that
    * are derived from [[Monad]].
    */
  trait Type[F[_]] extends Applicative.Type[F] {
    implicit def monad: Monad[F]
  }

  /** The `Monad.Instance` provides the means to combine
    * [[Monad]] instances with other type-classes.
    *
    * To be inherited by `Monad` instances.
    */
  trait Instance[F[_]] extends Monad[F] with Type[F] with Applicative.Instance[F] {
    override final def monad: Monad[F] = this
  }

  /** Provides syntax for [[Monad]]. */
  trait Syntax extends Serializable {
    implicit final def monadOps[F[_] : Monad, A](fa: F[A]): Ops[F, A] =
      new Ops(fa)
  }

  /** Extension methods for [[Monad]]. */
  final class Ops[F[_], A](self: F[A])(implicit F: Monad[F])
    extends Serializable {

    /** Extension method for [[Monad.flatMap]]. */
    def flatMap[B](f: A => F[B]): F[B] = F.flatMap(self)(f)
    /** Extension method for [[Monad.flatten]]. */
    def flatten[B](implicit ev: A <:< F[B]): F[B] =
    F.flatten(self.asInstanceOf[F[F[B]]])
  }
}

