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

/** A functor provides the `map` operation that allows lifting an `f`
  * function into the functor context and applying it.
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * To implement it in instances, inherit from [[FunctorClass]].
  *
  * Credit should be given where it is due.The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Functor[F[_]] extends Serializable {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}

object Functor extends FunctorSyntax {
  @inline def apply[F[_]](implicit F: Functor[F]): Functor[F] = F
}

/** The `FunctorClass` provides the means to combine [[Functor]]
  * instances with other type-classes.
  *
  *  To be inherited by `Functor` instances.
  */
trait FunctorClass[F[_]] extends Functor[F] {
  final def functor: Functor[F] = this
}

/** Provides syntax for [[Functor]]. */
trait FunctorSyntax extends Serializable {
  implicit def functorOps[F[_], A](fa: F[A])
    (implicit F: Functor[F]): FunctorSyntax.Ops[F, A] =
    new FunctorSyntax.Ops(fa)
}

object FunctorSyntax {
  class Ops[F[_], A](self: F[A])(implicit F: Functor[F])
    extends Serializable {

    def map[B](f: A => B): F[B] = F.map(self)(f)
  }
}
