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

/** The `Recoverable` type-class is the equivalent of
  * `ApplicativeError` or of `MonadError` from other libraries like
  * Cats or Scalaz. This type class allows one to abstract over
  * error-handling applicatives.
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * To implement it in instances, inherit from [[RecoverableClass]].
  *
  * Credit should be given where it is due.The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Recoverable[F[_], E] extends Serializable {
  def applicative: Applicative[F]

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
}

object Recoverable extends RecoverableSyntax {
  @inline def apply[F[_],E](implicit F: Recoverable[F,E]): Recoverable[F,E] = F
}

/** The `RecoverableClass` provides the means to combine
  * [[Recoverable]] instances with other type-classes.
  *
  * To be inherited by `Recoverable` instances.
  */
trait RecoverableClass[F[_],E]
  extends Recoverable[F,E] with ApplicativeClass[F] {

  final def recoverable: Recoverable[F,E] = this
}

trait RecoverableSyntax extends Serializable {
  implicit def recoverableOps[F[_],E,A](fa: F[A])
    (implicit F: Recoverable[F,E]): RecoverableSyntax.Ops[F,E,A] =
    new RecoverableSyntax.Ops(fa)
}

object RecoverableSyntax {
  class Ops[F[_], E, A](self: F[A])(implicit F: Recoverable[F,E])
    extends Serializable {

    def onErrorHandleWith(f: E => F[A]): F[A] =
      F.onErrorHandleWith(self)(f)
    def onErrorHandle(f: E => A): F[A] =
      F.onErrorHandle(self)(f)
    def onErrorRecoverWith(pf: PartialFunction[E, F[A]]): F[A] =
      F.onErrorRecoverWith(self)(pf)
    def onErrorRecover(pf: PartialFunction[E, A]): F[A] =
      F.onErrorRecover(self)(pf)
  }
}
