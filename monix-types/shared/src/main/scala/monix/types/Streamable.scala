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

import cats.{Eval, CoflatMap}
import simulacrum.typeclass
import scala.language.{higherKinds, implicitConversions}

@typeclass trait Streamable[F[_]]
  extends MonadFilter[F] with MonadConsError[F,Throwable] with Recoverable[F, Throwable]
  with Scannable[F] with FFoldable[F] with Zippable[F] with CoflatMap[F] {

  /** Lifts any `Iterable` into a `Sequenceable` type. */
  def fromIterable[A](ia: Iterable[A]): F[A]

  /** Given a partial function, filters and transforms the source by it. */
  def collect[A,B](fa: F[A])(pf: PartialFunction[A,B]): F[B] =
    map(filter(fa)(pf.isDefinedAt))(a => pf(a))

  /** Builds an empty instance that completes when the source completes. */
  def completed[A](fa: F[A]): F[A] =
    filter(fa)(a => false)

  /** Alias for [[completed]]. */
  final def ignoreElements[A](fa: F[A]): F[A] =
    completed(fa)

  /** Creates a sequence that eliminates duplicates from the source. */
  def distinct[A](fa: F[A]): F[A]

  /** Creates a sequence that eliminates duplicates from the source,
    * as determined by the given selector function that returns keys
    * for comparison.
    */
  def distinctByKey[A,Key](fa: F[A])(key: A => Key): F[A]

  /** Suppress duplicate consecutive items emitted by the source. */
  def distinctUntilChanged[A](fa: F[A]): F[A]

  /** Suppress duplicate consecutive items emitted by the source. */
  def distinctUntilChangedByKey[A,Key](fa: F[A])(key: A => Key): F[A]

  /** Returns a new sequence that will drop a maximum of
    * `n` elements from the start of the source sequence.
    */
  def drop[A](fa: F[A], n: Int): F[A]

  /** Drops the last `n` elements (from the end). */
  def dropLast[A](fa: F[A], n: Int): F[A]

  /** Returns a new sequence that will drop elements from
    * the start of the source sequence, for as long as the given
    * function `f` returns `true` and then stop.
    */
  def dropWhile[A](fa: F[A])(f: A => Boolean): F[A]

  /** Returns the first element in a sequence. */
  def headF[A](fa: F[A]): F[A] =
    take(fa, 1)

  /** Returns the first element in a sequence. */
  def headOrElseF[A](fa: F[A], default: Eval[A]): F[A] =
    map(foldLeftF(take(fa, 1), Option.empty[A])((_,a) => Some(a))) {
      case None => default.value
      case Some(a) => a
    }

  /** Returns the first element in a sequence.
    *
    * Alias for [[headOrElseF]].
    */
  def firstOrElseF[A](fa: F[A], default: Eval[A]): F[A] =
    headOrElseF(fa, default)

  /** Returns the last element in a sequence. */
  def lastF[A](fa: F[A]): F[A] =
    takeLast(fa, 1)

  /** Returns a new sequence with the first element dropped. */
  def tail[A](fa: F[A]): F[A] = drop(fa, 1)

  /** Returns a new sequence that will take a maximum of
    * `n` elements from the start of the source sequence.
    */
  def take[A](fa: F[A], n: Int): F[A]

  /** Returns a new sequence that will take a maximum of
    * `n` elements from the end of the source sequence.
    */
  def takeLast[A](fa: F[A], n: Int): F[A]

  /** Returns a new sequence that will take elements from
    * the start of the source sequence, for as long as the given
    * function `f` returns `true` and then stop.
    */
  def takeWhile[A](fa: F[A])(f: A => Boolean): F[A]

  /** Periodically gather items emitted by the source into bundles
    * of the specified size.
    */
  def buffer[A](fa: F[A])(count: Int): F[Seq[A]] =
    bufferSkipped(fa, count, count)

  /** Periodically gather items emitted by the source into bundles.
    *
    * For `count` and `skip` there are 3 possibilities:
    *
    *  1. in case `skip == count`, then there are no items dropped and
    *      no overlap, the call being equivalent to `buffer(count)`
    *  2. in case `skip < count`, then overlap between buffers
    *     happens, with the number of elements being repeated being
    *     `count - skip`
    *  3. in case `skip > count`, then `skip - count` elements start
    *     getting dropped between windows
    */
  def bufferSkipped[A](fa: F[A], count: Int, skip: Int): F[Seq[A]]

  /** Ends the sequence with the given elements. */
  def endWith[A](fa: F[A])(elems: Seq[A]): F[A] =
    followWith(fa, Eval.now(fromIterable(elems)))

  /** Starts the sequence with the given elements. */
  def startWith[A](fa: F[A])(elems: Seq[A]): F[A] =
    followWith(fromIterable(elems), Eval.now(fa))

  /** Given an `Ordering` returns the maximum element of the source. */
  def maxF[A](fa: F[A])(implicit A: Ordering[A]): F[A] = {
    val folded = foldLeftF(fa, Option.empty[A]) {
      case (None, a) => Some(a)
      case (Some(max), a) => Some(if (A.compare(max, a) < 0) a else max)
    }

    collect(folded) { case Some(max) => max }
  }

  /** Given a key extractor, finds the element with the maximum key. */
  def maxByF[A,B](fa: F[A])(f: A => B)(implicit B: Ordering[B]): F[A] = {
    val folded = foldLeftF(fa, Option.empty[(A,B)]) {
      case (None, a) => Some((a, f(a)))
      case (ref @ Some((prev,max)), elem) =>
        val newMax = f(elem)
        if (B.compare(max, newMax) < 0)
          Some((elem, newMax))
        else
          ref
    }

    collect(folded) { case Some((a,_)) => a }
  }

  /** Given an `Ordering` returns the maximum element of the source. */
  def minF[A](fa: F[A])(implicit A: Ordering[A]): F[A] = {
    val folded = foldLeftF(fa, Option.empty[A]) {
      case (None, a) => Some(a)
      case (Some(min), a) => Some(if (A.compare(min, a) > 0) a else min)
    }

    collect(folded) { case Some(min) => min }
  }

  /** Given a key extractor, finds the element with the minimum key. */
  def minByF[A,B](fa: F[A])(f: A => B)(implicit B: Ordering[B]): F[A] = {
    val folded = foldLeftF(fa, Option.empty[(A,B)]) {
      case (None, a) => Some((a, f(a)))
      case (ref @ Some((prev,min)), elem) =>
        val newMin = f(elem)
        if (B.compare(min, newMin) > 0)
          Some((elem, newMin))
        else
          ref
    }

    collect(folded) { case Some((a,_)) => a }
  }

  /** Check whether at least one element satisfies the predicate.
    *
    * If there are no elements, the result is `false`.
    */
  def existsF[A](fa: F[A])(p: A => Boolean): F[Boolean]

  /** Find the first element matching the predicate, if one exists. */
  def findOptF[A](fa: F[A])(p: A => Boolean): F[Option[A]]

  /** Check whether all elements satisfies the predicate.
    *
    * If at least one element doesn't satisfy the predicate,
    * the result is `false`.
    */
  def forAllF[A](fa: F[A])(p: A => Boolean): F[Boolean]

  /** Checks if the source sequence is empty. */
  def isEmptyF[A](fa: F[A]): F[Boolean]

  /** Checks if the source sequence is non-empty. */
  def nonEmptyF[A](fa: F[A]): F[Boolean]
}