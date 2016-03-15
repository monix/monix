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

import algebra.Monoid
import simulacrum.typeclass
import scala.language.{higherKinds, implicitConversions}

/** Data structures that can be folded to a summary value,
  * possibly lazily or asynchronously.
  *
  * The main operation is `foldLeftF` that folds `fa`
  * from left to right, or from first to last. Beyond this it
  * provides many other useful methods related to
  * folding over `F[A]` values.
  *
  * Note that a corresponding `foldRight` is not provided,
  * because that would be incompatible with large or infinite
  * streams.
  *
  * See: [[http://www.cs.nott.ac.uk/~pszgmh/fold.pdf A tutorial on the universality and expressiveness of fold]].
  */

@typeclass trait FFoldable[F[_]] {
  /** Left associative asynchronous fold on 'F' using the function 'f'. */
  def foldLeftF[A, S](fa: F[A], seed: S)(f: (S, A) => S): F[S]

  /** Given a sequences, produces a new sequence that will expose the
    * count of the source.
    */
  def countF[A](fa: F[A]): F[Long] =
    foldLeftF(fa, 0L)((acc,_) => acc + 1)

  /** Check whether at least one element satisfies the predicate.
    *
    * If there are no elements, the result is `false`.
    */
  def existsF[A](fa: F[A])(p: A => Boolean): F[Boolean] =
    foldLeftF(fa, false) { (acc, elem) => acc || p(elem) }

  /** Find the first element matching the predicate, if one exists. */
  def findOptF[A](fa: F[A])(p: A => Boolean): F[Option[A]] =
    foldLeftF(fa, Option.empty[A]) {
      (acc, elem) => acc match {
        case None => if (p(elem)) Some(elem) else None
        case ref @ Some(_) => ref
      }
    }

  /** Folds a `Monoid`. */
  def foldF[A](fa: F[A])(implicit A: Monoid[A]): F[A] =
    foldLeftF(fa, A.empty)(A.combine)

  /** Fold implemented by mapping `A` values into `B` and then
    * combining them using the given `Monoid[B]` instance.
    */
  def foldMapF[A, B](fa: F[A])(f: A => B)(implicit B: Monoid[B]): F[B] =
    foldLeftF(fa, B.empty)((b, a) => B.combine(b, f(a)))

  /** Check whether all elements satisfies the predicate.
    *
    * If at least one element doesn't satisfy the predicate,
    * the result is `false`.
    */
  def forAllF[A](fa: F[A])(p: A => Boolean): F[Boolean] =
    foldLeftF(fa, true) { (acc, elem) => acc && p(elem) }

  /** Checks if the source sequence is empty. */
  def isEmptyF[A](fa: F[A]): F[Boolean] =
    foldLeftF(fa, true)((_,_) => false)

  /** Checks if the source sequence is non-empty. */
  def nonEmptyF[A](fa: F[A]): F[Boolean] =
    foldLeftF(fa, false)((_,_) => true)

  /** Given a sequence of numbers, calculates a sum. */
  def sumF[A](fa: F[A])(implicit A: Numeric[A]): F[A] =
    foldLeftF(fa, A.zero)(A.plus)
}
