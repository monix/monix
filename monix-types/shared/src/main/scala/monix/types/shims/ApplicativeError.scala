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

package monix.types.shims

/** A shim for an `ApplicativeError` type-class, to be supplied by / translated to
  * libraries such as Cats or Scalaz.
  */
trait ApplicativeError[F[_],E] extends Applicative[F] {
  /** Lift an error into the `F` context. */
  def raiseError[A](e: E): F[A]

  /** Handle any error, potentially recovering from it, by mapping it to an
    * `F[A]` value.
    */
  def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A]

  /** Handle any error, by mapping it to an `A` value. */
  def handleError[A](fa: F[A])(f: E => A): F[A]

  /** Recover from certain errors by mapping them to an `A` value. */
  def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A]

  /** Recover from certain errors by mapping them to an `F[A]` value. */
  def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A]
}

object ApplicativeError {
  @inline def apply[F[_],E](implicit F: ApplicativeError[F,E]): ApplicativeError[F,E] = F
}

