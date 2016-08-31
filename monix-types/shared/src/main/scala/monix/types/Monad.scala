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
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * To implement it in instances, inherit from [[MonadClass]].
  *
  * Credit should be given where it is due.The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Monad[F[_]] extends Serializable {
  def applicative: Applicative[F]

  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def flatten[A](ffa: F[F[A]]): F[A] =
    flatMap(ffa)(x => x)
}

object Monad extends MonadSyntax {
  @inline def apply[F[_]](implicit F: Monad[F]): Monad[F] = F
}

/** The `MonadClass` provides the means to combine
  * [[Monad]] instances with other type-classes.
  *
  * To be inherited by `Monad` instances.
  */
trait MonadClass[F[_]] extends Monad[F] with ApplicativeClass[F] {
  final def monad: Monad[F] = this
}

/** Provides syntax for [[Monad]]. */
trait MonadSyntax extends Serializable {
  implicit def monadOps[F[_], A](fa: F[A])
    (implicit F: Monad[F]): MonadSyntax.Ops[F, A] =
    new MonadSyntax.Ops(fa)
}

object MonadSyntax {
  class Ops[F[_], A](self: F[A])(implicit F: Monad[F])
    extends Serializable {

    def flatMap[B](f: A => F[B]): F[B] =
      F.flatMap(self)(f)
    def flatten[B](implicit ev: A <:< F[B]): F[B] =
      F.flatten(self.asInstanceOf[F[F[B]]])
  }
}

