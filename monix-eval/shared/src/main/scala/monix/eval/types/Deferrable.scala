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

package monix.eval.types

/** A type-class describing computations that can be deferred. */
trait Deferrable[F[_]] {
  /** Lifts a strict value into the deferrable context. */
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
  def unit: F[Unit] = now(())

  /** Given a deferrable, memoizes its result on the first evaluation,
    * to be reused for subsequent evaluations.
    */
  def memoize[A](fa: F[A]): F[A]

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

  /** The Functor's mapping function. */
  def map[A, B](fa: F[A])(f: (A) => B): F[B]

  /** The Monad's bind operation. */
  def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B]

  /** Alias for `flatMap(identity)`. */
  def flatten[A](ffa: F[F[A]]): F[A]

  /** Transforms a list of deferrables into a deferrable of list. */
  def zipList[A](sources: Seq[F[A]]): F[Seq[A]]

  /** Combines the result of two deferrables. */
  def zipWith2[A1,A2,R](fa1: F[A1], fa2: F[A2])(f: (A1,A2) => R): F[R]

  /** Combines the result of two deferrables. */
  def zip2[A1,A2](fa1: F[A1], fa2: F[A2]): F[(A1,A2)] =
    zipWith2(fa1,fa2)((a1,a2) => (a1,a2))

  /** Combines the result of three deferrables. */
  def zip3[A1,A2,A3](fa1: F[A1], fa2: F[A2], fa3: F[A3]): F[(A1,A2,A3)] =
    zipWith3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))

  /** Combines the result of three deferrables. */
  def zipWith3[A1,A2,A3,R](fa1: F[A1], fa2: F[A2], fa3: F[A3])(f: (A1,A2,A3) => R): F[R] = {
    val fa12 = zip2(fa1, fa2)
    zipWith2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  /** Combines the result of four deferrables. */
  def zip4[A1,A2,A3,A4](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4]): F[(A1,A2,A3,A4)] =
    zipWith4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))

  /** Combines the result of four deferrables. */
  def zipWith4[A1,A2,A3,A4,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4])(f: (A1,A2,A3,A4) => R): F[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    zipWith2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  /** Combines the result of five deferrables. */
  def zip5[A1,A2,A3,A4,A5](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5]): F[(A1,A2,A3,A4,A5)] =
    zipWith5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))

  /** Combines the result of five deferrables. */
  def zipWith5[A1,A2,A3,A4,A5,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5])(f: (A1,A2,A3,A4,A5) => R): F[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipWith2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  /** Combines the result of six deferrables. */
  def zip6[A1,A2,A3,A4,A5,A6](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5], fa6: F[A6]): F[(A1,A2,A3,A4,A5,A6)] =
    zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** Combines the result of six deferrables. */
  def zipWith6[A1,A2,A3,A4,A5,A6,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5], fa6: F[A6])(f: (A1,A2,A3,A4,A5,A6) => R): F[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipWith2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }
}

object Deferrable {
  def apply[F[_]](implicit F: Deferrable[F]) = F
}