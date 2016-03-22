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
import simulacrum.typeclass
import scala.language.{higherKinds, implicitConversions}

/** A monad that can memoize non-strict values such that
  * evaluation only happens once.
  */
@typeclass trait Evaluable[F[_]] extends Monad[F] {
  /** Given an evaluable value, applies memoization such that
    * it gets evaluated only the first time and then the result
    * gets reused on subsequent evaluations.
    */
  def memoize[A](fa: F[A]): F[A]

  /** Lifts a strict value into an evaluable. */
  def now[A](a: A): F[A]

  /** Lifts a non-strict value into an evaluable,
    * with the value being evaluated every time the
    * returned instance is evaluated.
    */
  def evalAlways[A](a: => A): F[A] = now(a)

  /** Lifts a non-strict value into an evaluable and
    * memoizes it for subsequent evaluations such that
    * the given expression is evaluated only once.
    */
  def evalOnce[A](a: => A): F[A] =
    memoize(evalAlways(a))

  /** Promotes a non-strict value to a value of the same type. */
  def defer[A](fa: => F[A]): F[A] = flatten(evalAlways(fa))

  override def pure[A](a: A): F[A] = now(a)
  override def pureEval[A](x: Eval[A]): F[A] = evalAlways(x.value)
}
