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

  /** Folds a `Monoid`. */
  def foldF[A](fa: F[A])(implicit A: Monoid[A]): F[A] =
    foldLeftF(fa, A.empty)(A.combine)

  /** Fold implemented by mapping `A` values into `B` and then
    * combining them using the given `Monoid[B]` instance.
    */
  def foldMapF[A, B](fa: F[A])(f: A => B)(implicit B: Monoid[B]): F[B] =
    foldLeftF(fa, B.empty)((b, a) => B.combine(b, f(a)))

  /** Given a sequence of numbers, calculates a sum. */
  def sumF[A](fa: F[A])(implicit A: Numeric[A]): F[A] =
    foldLeftF(fa, A.zero)(A.plus)
}
