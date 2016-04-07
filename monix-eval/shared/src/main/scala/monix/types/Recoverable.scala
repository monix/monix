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

import monix.types.shims.Monad
import simulacrum.typeclass

/** A type-class for monadic contexts that can trigger `Throwable` errors
  * and that are recoverable.
  */
@typeclass trait Recoverable[F[_]] extends Monad[F] {
  /** Lifts an error into the monadic context. */
  def error[A](ex: Throwable): F[A]

  /** Turns the monadic context into one that exposes any
    * errors that might have happened.
    */
  def failed[A](fa: F[A]): F[Throwable]

  /** Mirrors the source, until the source throws an error, after which
    * it tries to fallback to the output of the given total function.
    *
    * See [[onErrorRecoverWith]] for the alternative accepting a partial function.
    */
  def onErrorHandleWith[A](fa: F[A])(f: Throwable => F[A]): F[A]

  /** Mirrors the source, but in case an error happens then use the
    * given total function to fallback to a given element for certain
    * errors.
    *
    * See [[onErrorRecover]] for the alternative accepting a partial function.
    */
  def onErrorHandle[A](fa: F[A])(f: Throwable => A): F[A]

  /** Mirrors the source, until the source throws an error, after which
    * it tries to fallback to the output of the given partial function.
    *
    * See [[onErrorHandleWith]] for the alternative accepting a total function.
    */
  def onErrorRecoverWith[A](fa: F[A])(pf: PartialFunction[Throwable, F[A]]): F[A]

  /** Mirrors the source, but in case an error happens then use the
    * given partial function to fallback to a given element for certain
    * errors.
    *
    * See [[onErrorHandle]] for the alternative accepting a total function.
    */
  def onErrorRecover[A](fa: F[A])(pf: PartialFunction[Throwable, A]): F[A]

  /** Mirrors the source, but if an error happens, then fallback to `other`. */
  def onErrorFallbackTo[A](fa: F[A], other: F[A]): F[A]

  /** In case an error happens, keeps retrying iterating the source from the start
    * for `maxRetries` times.
    *
    * So the number of attempted iterations of the source will be `maxRetries+1`.
    */
  def onErrorRetry[A](fa: F[A], maxRetries: Long): F[A]

  /** In case an error happens, retries iterating the source from the
    * start for as long as the given predicate returns true.
    */
  def onErrorRetryIf[A](fa: F[A])(p: Throwable => Boolean): F[A]
}
