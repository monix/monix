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

package monix.laws

import cats.data.Xor
import cats.laws.{IsEq, MonadErrorLaws}
import monix.types.Recoverable
import scala.language.higherKinds

trait RecoverableLaws[F[_],E] extends MonadErrorLaws[F,E] {
  implicit def F: Recoverable[F,E]

  def recoverableHandleErrorWithIsAliased[A](e: E, f: E => F[A]): IsEq[F[A]] =
    F.handleErrorWith(F.raiseError[A](e))(f) <-> F.onErrorHandleWith(F.raiseError[A](e))(f)

  def recoverableHandleErrorIsAliased[A](e: E, f: E => A): IsEq[F[A]] =
    F.handleError(F.raiseError[A](e))(f) <-> F.onErrorHandle(F.raiseError[A](e))(f)

  def recoverableRecoverWithIsAliased[A](e: E, f: E => F[A]): IsEq[F[A]] = {
    val pf = PartialFunction(f)
    val err = F.raiseError[A](e)
    F.recoverWith(F.raiseError[A](e))(pf) <-> F.onErrorRecoverWith(err)(pf)
  }

  def recoverableRecoverIsAliased[A](e: E, f: E => A): IsEq[F[A]] = {
    val pf = PartialFunction(f)
    val err = F.raiseError[A](e)
    F.recover(F.raiseError[A](e))(pf) <-> F.onErrorRecover(err)(pf)
  }

  def recoverableOnErrorFallbackToConsistentWithOnErrorHandleWith[A](e: E, f: E => F[A]): IsEq[F[A]] = {
    val fa = F.raiseError[A](e)
    F.onErrorHandleWith(fa)(f) <-> F.onErrorFallbackTo(fa, f(e))
  }

  def recoverableRetryMirrorsSourceOnSuccess[A](fa: F[A]): IsEq[F[A]] =
    fa <-> F.onErrorRetry(fa, 10)

  def recoverableRetryMirrorsErrorOnFailure[A](e: E): IsEq[F[A]] =
    F.raiseError[A](e) <-> F.onErrorRetry(F.raiseError(e), 10)

  def recoverableRetryIfMirrorsSourceOnFalsePredicate[A](v: E Xor A): IsEq[F[A]] = {
    val fa = v.map(F.pure).leftMap(F.raiseError[A]).fold(identity, identity)
    val p: E => Boolean = e => false
    F.onErrorRetryIf(fa)(p) <-> F.mapAttempt(fa)(identity)
  }

  def recoverableRetryIfMirrorsSuccessOnTruePredicate[A](a: A): IsEq[F[A]] = {
    val fa = F.pure(a)
    val p: E => Boolean = e => true
    F.onErrorRetryIf(fa)(p) <-> fa
  }

  def recoverableMapAttemptIdentity[A](a: E Xor A): IsEq[F[A]] = {
    val fa = a match {
      case Xor.Left(err) => F.raiseError[A](err)
      case Xor.Right(value) => F.pure(value)
    }

    fa <-> F.mapAttempt(fa)(identity)
  }

  def recoverableMapAttemptComposition[A,B,C](source: F[E Xor A], fab: A => B, fbc: B => C): IsEq[F[C]] = {
    val fa = F.flatMap(source) {
      case Xor.Left(e) => F.raiseError[A](e)
      case Xor.Right(a) => F.pure(a)
    }

    F.mapAttempt(F.mapAttempt(fa)(_.map(fab)))(_.map(fbc)) <-> F.mapAttempt(fa)(_.map(fab andThen fbc))
  }
}

object RecoverableLaws {
  def apply[F[_], E](implicit ev: Recoverable[F,E]): RecoverableLaws[F, E] =
    new RecoverableLaws[F, E] { def F: Recoverable[F, E] = ev }
}