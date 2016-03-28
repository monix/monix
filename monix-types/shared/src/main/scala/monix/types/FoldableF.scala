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

import cats.{MonoidK, Monoid, Functor}
import simulacrum.typeclass
import scala.collection.mutable
import scala.language.{higherKinds, implicitConversions}

/** Data structures that can be (left) folded to a summary value,
  * possibly lazily or asynchronously.
  *
  * The main operation is `foldLeftF` that folds `fa`
  * from left to right, or from first to last. Beyond this it
  * provides other useful methods related to
  * folding over `F[A]` values.
  *
  * Compared to the normal `Foldable` type-class, this one
  * keeps the original context, potentially delaying execution
  * if `F` is lazy or asynchronous.
  *
  * A `foldRight` is not provided by this type-class, because it isn't
  * implementable for all stream types, as a `foldRight` assumes at least a way
  * to decompose a stream into a head and a (deferred) tail. For asynchronous
  * streams that's actually a big limitation because for example in a
  * push-based model the producer-consumer relationship is usually a one on one
  * and switching consumers is error prone, if not impossible.
  * To solve some use-cases of `foldRight`, a `foldWhileF` utility is provided,
  * which works like `foldLeft`, but with the ability to short-circuit the fold.
  *
  * Must obey the laws in `monix.laws.FoldableFLaws`.
  */
@typeclass trait FoldableF[F[_]] extends Functor[F] {
  /** Left associative fold on `F` with the ability to short-circuit the process.
    *
    * This fold works for as long as the provided function keeps returning `true`
    * as the first member of its result and the streaming isn't completed.
    * If the provided fold function returns a `false` then the folding will
    * stop and the generated result will be the second member
    * of the resulting tuple.
    *
    * @note [[foldLeftF]] can be described in terms of [[foldWhileF]] by always
    *       returning `true` in the resulting tuple of the provided function
    * @param f is the folding function, returning `(true, state)` if the fold has
    *          to be continued, or `(false, state)` if the fold has to be stopped
    *          and the rest of the values to be ignored.
    */
  def foldWhileF[A, S](fa: F[A], seed: S)(f: (S, A) => (Boolean, S)): F[S]

  /** Left associative fold on 'F' using the function 'f'. */
  def foldLeftF[A, S](fa: F[A], seed: S)(f: (S, A) => S): F[S] =
    foldWhileF(fa, seed)((acc, a) => (true, acc))

  /** Fold implemented by mapping `A` values into `B` and then
    * combining them using the given `Monoid[B]` instance.
    */
  def foldMapF[A, B](fa: F[A])(f: A => B)(implicit B: Monoid[B]): F[B] =
    foldLeftF(fa, B.empty)((b, a) => B.combine(b, f(a)))

  /** Folds a `Monoid`. */
  def foldF[A](fa: F[A])(implicit A: Monoid[A]): F[A] =
    foldLeftF(fa, A.empty)(A.combine)

  /** Fold implemented using the given `MonoidK[G]` instance.
    *
    * This method is identical to [[foldF]], except that it works
    * with the universal `MonoidK`.
    */
  def foldKF[G[_], A](fga: F[G[A]])(implicit G: MonoidK[G]): F[G[A]] =
    foldF(fga)(G.algebra)

  /** Given a sequences, produces a new sequence that will expose the
    * count of the source.
    */
  def countF[A](fa: F[A]): F[Long] =
    foldLeftF(fa, 0L)((acc,_) => acc + 1)

  /** Given a sequence of numbers, calculates a sum. */
  def sumF[A](fa: F[A])(implicit A: Numeric[A]): F[A] =
    foldLeftF(fa, A.zero)(A.plus)

  /** Find the first element matching the predicate, if one exists. */
  def findF[A](fa: F[A])(f: A => Boolean): F[Option[A]] =
    foldWhileF(fa, Option.empty[A])((acc, e) => if (f(e)) (true, Some(e)) else (false, acc))

  /** Check whether at least one element satisfies the predicate. */
  def existsF[A](fa: F[A])(p: A => Boolean): F[Boolean] =
    foldWhileF(fa, false)((acc, e) => if (p(e)) (true, true) else (false, acc))

  /** Check whether all elements satisfy the predicate. */
  def forallF[A](fa: F[A])(p: A => Boolean): F[Boolean] =
    foldWhileF(fa, true)((acc, e) => if (!p(e)) (true, false) else (false, acc))

  /** Aggregates elements in a `List` and preserves order. */
  def toListF[A](fa: F[A]): F[List[A]] = {
    val folded = foldLeftF(fa, mutable.ListBuffer.empty[A]) { (acc, a) => acc += a }
    map(folded)(_.toList)
  }

  /** Returns true if there are no elements, false otherwise. */
  def isEmptyF[A](fa: F[A]): F[Boolean] =
    foldWhileF(fa, true)((acc, e) => (true, false))

  /** Returns true if there are elements, false otherwise. */
  def nonEmptyF[A](fa: F[A]): F[Boolean] =
    foldWhileF(fa, false)((acc, e) => (true, true))
}
