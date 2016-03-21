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

import cats.Eval
import cats.data.Xor
import scala.language.higherKinds

/** Enhancements for the `MonadError` type-class from Cats. */
trait Recoverable[F[_], E] extends MonadError[F, E] {
  /** In case the source emits an error, then emit that error. */
  def failed[A](fa: F[A]): F[E]

  /** Mirrors the source, until the source throws an error, after which
    * it tries to fallback to the output of the given partial function.
    *
    * Obviously, the implementation needs to be stack-safe.
    */
  def onErrorRecoverWith[A](fa: F[A])(f: E => F[A]): F[A]

  /** Mirrors the source, but in case an error happens then use the
    * given partial function to fallback to a given element for certain
    * errors.
    */
  def onErrorRecover[A](fa: F[A])(f: E => A): F[A] =
    onErrorRecoverWith(fa) { case ex => pure(f(ex)) }

  /** Mirrors the source, but if an error happens, then fallback to `other`. */
  def onErrorFallbackTo[A](fa: F[A], other: Eval[F[A]]): F[A] =
    onErrorRecoverWith(fa) { case _ => other.value }

  /** In case an error happens, keeps retrying iterating the source from the start
    * for `maxRetries` times.
    *
    * So the number of attempted iterations of the source will be `maxRetries+1`.
    */
  def onErrorRetry[A](fa: F[A], maxRetries: Long): F[A] = {
    require(maxRetries >= 0, "maxRetries should be positive")

    if (maxRetries == 0) fa
    else onErrorRecoverWith(fa) { case _ => onErrorRetry(fa, maxRetries-1) }
  }

  /** In case an error happens, retries iterating the source from the
    * start for as long as the given predicate returns true.
    */
  def onErrorRetryIf[A](fa: F[A])(p: E => Boolean): F[A] =
    onErrorRecoverWith(fa) { case ex if p(ex) => onErrorRetryIf(fa)(p) }

  /** Applies the mapping function on the attempted source. */
  def mapAttempt[A,S](fa: F[A])(f: Xor[E,A] => Xor[E,S]): F[S] =
    flatMap(attempt(fa)) { source =>
      f(source) match {
        case Xor.Left(error) => raiseError(error)
        case Xor.Right(result) => pure(result)
      }
    }

  // From ApplicativeError
  final override def handleErrorWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
    onErrorRecoverWith(fa) { case ex => f(ex) }
  // From ApplicativeError
  final override def handleError[A](fa: F[A])(f: (E) => A): F[A] =
    onErrorRecover(fa) { case ex => f(ex) }
  // From ApplicativeError
  final override def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
    onErrorRecover(fa)(pf)
  // From ApplicativeError
  final override def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
    onErrorRecoverWith(fa)(pf)
}
