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

/** The `MonadError` type-class describes monads that can do error handling.
  *
  * To implement `MonadError`:
  *
  *  - inherit from [[MonadError.Type]] in derived type-classes
  *  - inherit from [[MonadError.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scato
  * project by Aloïs Cochard and [[https://github.com/scalaz/scalaz/ Scalaz 8]]
  * and the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait MonadError[F[_], E] extends Serializable with Monad.Type[F] {
  self: MonadError.Instance[F,E] =>

  /** Lift an error into the `F` context. */
  def raiseError[A](e: E): F[A]

  /** Handle any error, potentially recovering from it, by mapping it to
    * an `F[A]` value.
    */
  def onErrorHandleWith[A](fa: F[A])(f: E => F[A]): F[A]

  /** Handle any error, by mapping it to an `A` value. */
  def onErrorHandle[A](fa: F[A])(f: E => A): F[A]

  /** Recover from certain errors by mapping them to an `F[A]` value. */
  def onErrorRecoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A]

  /** Recover from certain errors by mapping them to an `A` value. */
  def onErrorRecover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A]

  /** Handle errors by exposing them into [[scala.util.Either]] values.
    *
    * Returns `Right(value)` for successful values or `Left(error)` in
    * case a failure happened.
    */
  def attempt[A](fa: F[A]): F[Either[E, A]] =
    onErrorHandle(map(fa)(Right(_) : Either[E, A]))(Left(_))
}

object MonadError {
  @inline def apply[F[_],E](implicit F: MonadError[F,E]): MonadError[F,E] = F

  /** The `MonadError.Type` should be inherited in type-classes that
    * are derived from [[MonadError]].
    */
  trait Type[F[_],E] extends Monad.Type[F] {
    implicit def monadError: MonadError[F,E]
  }

  /** The `MonadError.Instance` provides the means to combine
    * [[MonadError]] instances with other type-classes.
    *
    * To be inherited by `MonadError` instances.
    */
  trait Instance[F[_],E] extends MonadError[F,E] with Type[F,E]
    with Monad.Instance[F] {

    override final def monadError: MonadError[F,E] = this
  }

  trait Syntax extends Serializable {
    implicit final def monadErrorOps[F[_],E,A](fa: F[A])(implicit F: MonadError[F,E]): Ops[F,E,A] =
      new Ops(fa)
  }

  /** Extension methods for [[MonadError]]. */
  final class Ops[F[_], E, A](self: F[A])(implicit F: MonadError[F,E])
    extends Serializable {

    /** Extension method for [[MonadError.attempt]]. */
    def attempt: F[A] =
      macro Macros.monadErrorAttempt

    /** Extension method for [[MonadError.onErrorHandleWith]]. */
    def onErrorHandleWith(f: E => F[A]): F[A] =
      macro Macros.monadErrorHandleWith

    /** Extension method for [[MonadError.onErrorHandle]]. */
    def onErrorHandle(f: E => A): F[A] =
      macro Macros.monadErrorHandle

    /** Extension method for [[MonadError.onErrorRecoverWith]]. */
    def onErrorRecoverWith(pf: PartialFunction[E, F[A]]): F[A] =
      macro Macros.monadErrorRecoverWith

    /** Extension method for [[MonadError.onErrorRecover]]. */
    def onErrorRecover(pf: PartialFunction[E, A]): F[A] =
      macro Macros.monadErrorRecover
  }

  /** Laws for [[MonadError]]. */
  trait Laws[F[_], E] extends Monad.Laws[F] with Type[F,E] {
    private def M = monad
    private def E = monadError
    private def A = applicative

    def monadErrorLeftZero[A, B](e: E, f: A => F[B]): IsEquiv[F[B]] =
      M.flatMap(E.raiseError[A](e))(f) <-> E.raiseError[B](e)

    def applicativeErrorHandleWith[A](e: E, f: E => F[A]): IsEquiv[F[A]] =
      E.onErrorHandleWith(E.raiseError[A](e))(f) <-> f(e)

    def applicativeErrorHandle[A](e: E, f: E => A): IsEquiv[F[A]] =
      E.onErrorHandle(E.raiseError[A](e))(f) <-> A.pure(f(e))

    def onErrorHandleWithPure[A](a: A, f: E => F[A]): IsEquiv[F[A]] =
      E.onErrorHandleWith(A.pure(a))(f) <-> A.pure(a)

    def onErrorHandlePure[A](a: A, f: E => A): IsEquiv[F[A]] =
      E.onErrorHandle(A.pure(a))(f) <-> A.pure(a)

    def onErrorHandleWithConsistentWithRecoverWith[A](fa: F[A], f: E => F[A]): IsEquiv[F[A]] =
      E.onErrorHandleWith(fa)(f) <-> E.onErrorRecoverWith(fa)(PartialFunction(f))

    def onErrorHandleConsistentWithRecover[A](fa: F[A], f: E => A): IsEquiv[F[A]] =
      E.onErrorHandle(fa)(f) <-> E.onErrorRecover(fa)(PartialFunction(f))

    def recoverConsistentWithRecoverWith[A](fa: F[A], pf: PartialFunction[E, A]): IsEquiv[F[A]] =
      E.onErrorRecover(fa)(pf) <-> E.onErrorRecoverWith(fa)(pf andThen A.pure)

    def raiseErrorAttempt[A](e: E): IsEquiv[F[Either[E, A]]] =
      E.attempt(E.raiseError[A](e)) <-> A.pure(Left(e) : Either[E, A])

    def pureAttempt[A](a: A): IsEquiv[F[Either[E, A]]] =
      E.attempt(A.pure(a)) <-> A.pure(Right(a))
  }
}