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

  def suspend[A](fa: => F[A]): F[A]

  protected def defaultSuspend[A](fa: => F[A]): F[A] =
    flatten(eval(fa))
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
  final class Ops[F[_], A](val self: F[A])(implicit val F: Monad[F])
    extends Serializable {

    /** Extension method for [[Monad.flatMap]]. */
    def flatMap[B](f: A => F[B]): F[B] =
      macro Macros.monadFlatMap
    /** Extension method for [[Monad.flatten]]. */
    def flatten[B](implicit ev: A <:< F[B]): F[B] =
      macro Macros.monadFlatten
  }

  /** Laws for [[Monad]]. */
  trait Laws[F[_]] extends Applicative.Laws[F] with Type[F] {
    private def M = monad
    private def F = functor
    private def A = applicative

    def flatMapAssociativity[A, B, C](fa: F[A], f: A => F[B], g: B => F[C]): IsEquiv[F[C]] =
      M.flatMap(M.flatMap(fa)(f))(g) <-> M.flatMap(fa)(a => M.flatMap(f(a))(g))

    def flatMapConsistentApply[A, B](fa: F[A], fab: F[A => B]): IsEquiv[F[B]] =
      A.ap(fab)(fa) <-> M.flatMap(fab)(f => F.map(fa)(f))

    def flatMapConsistentMap2[A, B, C](fa: F[A], fb: F[B], f: (A,B) => C): IsEquiv[F[C]] =
      A.map2(fa,fb)(f) <-> M.flatMap(fa)(a => F.map(fb)(b => f(a,b)))

    def suspendEquivalenceWithEval[A](a: A): IsEquiv[F[A]] =
      M.suspend(A.pure(a)) <-> A.eval(a)

    def evalEquivalenceWithSuspend[A](fa: F[A]): IsEquiv[F[A]] =
      M.flatten[A](A.eval(fa)) <-> M.suspend(fa)
  }
}

