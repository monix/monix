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

/** A type-class providing the `coflatMap` operation, the dual of
  * `flatMap`.
  *
  * To implement `Cobind`:
  *
  *  - inherit from [[Cobind.Type]] in derived type-classes
  *  - inherit from [[Cobind.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scato
  * project by Aloïs Cochard and [[https://github.com/scalaz/scalaz/ Scalaz 8]]
  * and the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Cobind[F[_]] extends Serializable with Functor.Type[F] {
  self: Cobind.Instance[F] =>

  def coflatMap[A, B](fa: F[A])(f: F[A] => B): F[B]
  def coflatten[A](fa: F[A]): F[F[A]] =
    coflatMap(fa)(fa => fa)
}

object Cobind {
  @inline def apply[F[_]](implicit F: Cobind[F]): Cobind[F] = F

  /** The `Cobind.Type` should be inherited in type-classes that
    * are derived from [[Cobind]].
    */
  trait Type[F[_]] extends Functor.Type[F] {
    implicit def cobind: Cobind[F]
  }

  /** The `Cobind.Instance` provides the means to combine
    * [[Cobind]] instances with other type-classes.
    *
    * To be inherited by `CoflatMap` instances.
    */
  trait Instance[F[_]] extends Cobind[F] with Type[F]
    with Functor.Instance[F] {

    override final def cobind: Cobind[F] = this
  }

  /** Provides syntax for [[Cobind]]. */
  trait Syntax extends Serializable {
    implicit final def cobindOps[F[_] : Cobind, A](fa: F[A]): Ops[F, A] =
      new Ops(fa)
  }

  /** Extension methods for [[Cobind]]. */
  final class Ops[F[_], A](self: F[A])(implicit F: Cobind[F])
    extends Serializable {

    /** Extension method for [[Cobind.coflatMap]]. */
    def coflatMap[B](f: F[A] => B): F[B] = F.coflatMap(self)(f)
    /** Extension method for [[Cobind.coflatten]]. */
    def coflatten: F[F[A]] = F.coflatten(self)
  }

  /** Laws for [[Cobind]]. */
  trait Laws[F[_]] extends Functor.Laws[F] with Type[F] {
    private def C = cobind
    private def F = functor

    def coflatMapAssociativity[A, B, C](fa: F[A], f: F[A] => B, g: F[B] => C): IsEquiv[F[C]] =
      C.coflatMap(C.coflatMap(fa)(f))(g) <-> C.coflatMap(fa)(x => g(C.coflatMap(x)(f)))

    def coflattenThroughMap[A](fa: F[A]): IsEquiv[F[F[F[A]]]] =
      C.coflatten(C.coflatten(fa)) <-> F.map(C.coflatten(fa))(C.coflatten)

    def coflattenCoherence[A, B](fa: F[A], f: F[A] => B): IsEquiv[F[B]] =
      C.coflatMap(fa)(f) <-> F.map(C.coflatten(fa))(f)

    def coflatMapIdentity[A, B](fa: F[A]): IsEquiv[F[F[A]]] =
      C.coflatten(fa) <-> C.coflatMap(fa)(identity)
  }
}