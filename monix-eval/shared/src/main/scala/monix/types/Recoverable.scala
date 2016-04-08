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

import monix.types.shims.Applicative

/** A type-class for monadic contexts that can trigger `E` errors
  * and that are recoverable.
  */
trait Recoverable[F[_], E] extends Applicative[F] {
  /** Lifts an error into context. */
  def raiseError[A](e: E): F[A]

  /** Turns the monadic context into one that exposes any
    * errors that might have happened.
    */
  def failed[A](fa: F[A]): F[E]

  /** Mirrors the source, but if an error happens, then fallback to `other`. */
  def onErrorFallbackTo[A](fa: F[A], other: F[A]): F[A]

  /** Mirrors the source, until the source throws an error, after which
    * it tries to fallback to the output of the given total function.
    *
    * See [[onErrorRecoverWith]] for the alternative accepting a partial function.
    */
  def onErrorHandleWith[A](fa: F[A])(f: E => F[A]): F[A]

  /** Mirrors the source, but in case an error happens then use the
    * given total function to fallback to a given element for certain
    * errors.
    *
    * See [[onErrorRecover]] for the alternative accepting a partial function.
    */
  def onErrorHandle[A](fa: F[A])(f: E => A): F[A]

  /** Mirrors the source, until the source throws an error, after which
    * it tries to fallback to the output of the given partial function.
    *
    * See [[onErrorHandleWith]] for the alternative accepting a total function.
    */
  def onErrorRecoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A]

  /** Mirrors the source, but in case an error happens then use the
    * given partial function to fallback to a given element for certain
    * errors.
    *
    * See [[onErrorHandle]] for the alternative accepting a total function.
    */
  def onErrorRecover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A]
}

object Recoverable {
  @inline def apply[F[_],E](implicit F: Recoverable[F,E]): Recoverable[F,E] = F
}