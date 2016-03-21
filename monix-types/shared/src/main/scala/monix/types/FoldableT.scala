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
import monix.async.Task
import simulacrum.typeclass
import scala.language.{higherKinds, implicitConversions}

/** Data structures that can be folded to an asynchronous
  * summary value.
  *
  * The main operation is `foldLeftT` that folds `fa`
  * from left to right, or from first to last, returning a
  * `Task` as a result.
  *
  * Note that a corresponding `foldLeft` is not provided,
  * because that would be incompatible with large or infinite
  * streams.
  */
@typeclass trait FoldableT[F[_]] {
  /** Left associative asynchronous fold on 'F' using the function 'f'. */
  def foldLeftT[A, S](fa: F[A], seed: S)(f: (S, A) => S): Task[S]

  /** Given a sequences, produces a new sequence that will expose the
    * count of the source.
    */
  def countT[A](fa: F[A]): Task[Long] =
    foldLeftT(fa, 0L)((acc,_) => acc + 1)

  /** Folds a `Monoid`. */
  def foldT[A](fa: F[A])(implicit A: Monoid[A]): Task[A] =
    foldLeftT(fa, A.empty)(A.combine)

  /** Fold implemented by mapping `A` values into `B` and then
    * combining them using the given `Monoid[B]` instance.
    */
  def foldMapT[A, B](fa: F[A])(f: A => B)(implicit B: Monoid[B]): Task[B] =
    foldLeftT(fa, B.empty)((b, a) => B.combine(b, f(a)))

  /** Given a sequence of numbers, calculates a sum. */
  def sumT[A](fa: F[A])(implicit A: Numeric[A]): Task[A] =
    foldLeftT(fa, A.zero)(A.plus)

  /** Given a sequence, return a Task. */
  def completedT[A](fa: F[A]): Task[Unit] =
    foldLeftT(fa, ())((_,_) => ())
}
