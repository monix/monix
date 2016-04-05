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

package monix.eval

import simulacrum.typeclass

import scala.language.{higherKinds, implicitConversions}

/** A type-class describing computations that can be delayed. */
@typeclass trait Evaluable[F[_]] {
  def now[A](a: A): F[A]
  def error[A](ex: Throwable): F[A]
  def defer[A](fa: => F[A]): F[A]
  def evalOnce[A](f: => A): F[A]
  def evalAlways[A](f: => A): F[A]
  def unit: F[Unit]
  def memoize[A](fa: F[A]): F[A]

  def flatMap[A,B](fa: F[A])(f: A => F[B]): F[B]
  def flatten[A](ffa: F[F[A]]): F[A]
  def failed[A](fa: F[A]): F[Throwable]

  def map[A,B](fa: F[A])(f: A => B): F[B]
  def onErrorHandleWith[A](fa: F[A])(f: Throwable => F[A]): F[A]
  def onErrorRecoverWith[A](fa: F[A])(pf: PartialFunction[Throwable, F[A]]): F[A]
  def onErrorHandle[A](fa: F[A])(f: Throwable => A): F[A]
  def onErrorRecover[A](fa: F[A])(pf: PartialFunction[Throwable, A]): F[A]
  def onErrorFallbackTo[A](fa: F[A], fallback: F[A]): F[A]
  def onErrorRetry[A](fa: F[A], maxRetries: Long): F[A]
  def onErrorRetryIf[A](fa: F[A])(p: Throwable => Boolean): F[A]

  def zipList[A](sources: Seq[F[A]]): F[Seq[A]]
  def zipWith2[A1,A2,R](fa1: F[A1], fa2: F[A2])(f: (A1,A2) => R): F[R]

  def zip2[A1,A2](fa1: F[A1], fa2: F[A2]): F[(A1,A2)] =
    zipWith2(fa1,fa2)((a1,a2) => (a1,a2))
  def zip3[A1,A2,A3](fa1: F[A1], fa2: F[A2], fa3: F[A3]): F[(A1,A2,A3)] =
    zipWith3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))
  def zip4[A1,A2,A3,A4](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4]): F[(A1,A2,A3,A4)] =
    zipWith4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))
  def zip5[A1,A2,A3,A4,A5](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5]): F[(A1,A2,A3,A4,A5)] =
    zipWith5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))

  def zipWith3[A1,A2,A3,R](fa1: F[A1], fa2: F[A2], fa3: F[A3])(f: (A1,A2,A3) => R): F[R] = {
    val fa12 = zip2(fa1, fa2)
    zipWith2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  def zipWith4[A1,A2,A3,A4,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4])(f: (A1,A2,A3,A4) => R): F[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    zipWith2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  def zipWith5[A1,A2,A3,A4,A5,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5])(f: (A1,A2,A3,A4,A5) => R): F[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipWith2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  def zip6[A1,A2,A3,A4,A5,A6](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5], fa6: F[A6]): F[(A1,A2,A3,A4,A5,A6)] =
    zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  def zipWith6[A1,A2,A3,A4,A5,A6,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5], fa6: F[A6])(f: (A1,A2,A3,A4,A5,A6) => R): F[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipWith2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }
}
