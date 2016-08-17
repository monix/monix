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
import simulacrum.typeclass

@typeclass(excludeParents = List("Applicative"))
trait Deferrable[F[_]] extends Applicative[F] {
  /** Lifts a strict value into the deferrable context.
    *
    * Alias for `Applicative.pure`.
    */
  def now[A](a: A): F[A]

  /** Builds deferrable instances from the given factory. */
  def defer[A](fa: => F[A]): F[A]

  /** Lifts a non-strict value into the deferrable context,
    * but memoizes it for subsequent evaluations.
    */
  def evalOnce[A](f: => A): F[A]

  /** Lifts a non-strict value into the deferrable context. */
  def evalAlways[A](f: => A): F[A]

  /** The `Unit` lifted into the deferrable context. */
  def unit: F[Unit]

  /** Given a deferrable, memoizes its result on the first evaluation,
    * to be reused for subsequent evaluations.
    */
  def memoize[A](fa: F[A]): F[A]

  override def pure[A](a: A): F[A] = now(a)
  override def pureEval[A](a: => A): F[A] = evalAlways(a)
}
