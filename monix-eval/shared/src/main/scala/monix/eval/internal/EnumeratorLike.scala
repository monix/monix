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

package monix.eval.internal

import monix.eval.ConsStream
import monix.types.Evaluable
import scala.collection.{LinearSeq, immutable}
import language.higherKinds

/** Common implementation between [[monix.eval.TaskEnumerator]]
  * and [[monix.eval.CoevalEnumerator]].
  */
private[eval] abstract
class EnumeratorLike[+A, F[_] : Evaluable, Self[+T] <: EnumeratorLike[T, F, Self]] {
  self: Self[A] =>

  def stream: ConsStream[A,F]
  protected def transform[B](f: ConsStream[A,F] => ConsStream[B,F]): Self[B]

  /** Filters the iterator by the given predicate function,
    * returning only those elements that match.
    */
  final def filter(p: A => Boolean): Self[A] =
    transform(_.filter(p))

  /** Returns a new iterable by mapping the supplied function
    * over the elements of the source.
    */
  final def map[B](f: A => B): Self[B] =
    transform(_.map(f))

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => Self[B]): Self[B] =
    transform(_.flatMap(a => f(a).stream))

  /** If the source is an async iterable generator, then
    * concatenates the generated async iterables.
    */
  final def flatten[B](implicit ev: A <:< Self[B]): Self[B] =
    flatMap(x => x)

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => Self[B]): Self[B] =
    flatMap(f)

  /** Alias for [[concat]]. */
  final def concat[B](implicit ev: A <:< Self[B]): Self[B] =
    flatten

  /** Appends the given iterable to the end of the source,
    * effectively concatenating them.
    */
  final def ++[B >: A](rhs: Self[B]): Self[B] =
    transform(_ ++ rhs.stream)


  /** Left associative fold using the function 'f'.
    *
    * On execution the iterable will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  def foldLeftL[S](seed: S)(f: (S,A) => S): F[S] =
    stream.foldLeftL(seed)(f)

  /** Left associative fold with the ability to short-circuit the process.
    *
    * This fold works for as long as the provided function keeps returning `true`
    * as the first member of its result and the streaming isn't completed.
    * If the provided fold function returns a `false` then the folding will
    * stop and the generated result will be the second member
    * of the resulting tuple.
    *
    * @param f is the folding function, returning `(true, state)` if the fold has
    *          to be continued, or `(false, state)` if the fold has to be stopped
    *          and the rest of the values to be ignored.
    */
  def foldWhileL[S](seed: S)(f: (S, A) => (Boolean, S)): F[S] =
    stream.foldWhileL(seed)(f)

  /** Right associative lazy fold on `Self` using the
    * folding function 'f'.
    *
    * This method evaluates `lb` lazily (in some cases it will not be
    * needed), and returns a lazy value. We are using `(A, Eval[B]) =>
    * Eval[B]` to support laziness in a stack-safe way. Chained
    * computation should be performed via .map and .flatMap.
    *
    * For more detailed information about how this method works see the
    * documentation for `Eval[_]`.
    */
  def foldRightL[B](lb: F[B])(f: (A, F[B]) => F[B]): F[B] =
    stream.foldRightL(lb)(f)

  /** Find the first element matching the predicate, if one exists. */
  def findL[B >: A](p: B => Boolean): F[Option[B]] =
    stream.findL(p)

  /** Count the total number of elements. */
  def countL: F[Long] =
    stream.countL

  /** Given a sequence of numbers, calculates a sum. */
  def sumL[B >: A](implicit B: Numeric[B]): F[B] =
    stream.sumL

  /** Check whether at least one element satisfies the predicate. */
  def existsL(p: A => Boolean): F[Boolean] =
    stream.existsL(p)

  /** Check whether all elements satisfy the predicate. */
  def forallL(p: A => Boolean): F[Boolean] =
    stream.forallL(p)

  /** Aggregates elements in a `List` and preserves order. */
  def toListL[B >: A]: F[List[B]] =
    stream.toListL

  /** Returns true if there are no elements, false otherwise. */
  def isEmptyL: F[Boolean] =
    stream.isEmptyL

  /** Returns true if there are elements, false otherwise. */
  def nonEmptyL: F[Boolean] =
    stream.nonEmptyL

  /** Returns the first element in the iterable, as an option. */
  def headL[B >: A]: F[Option[B]] =
    stream.headL

  /** Alias for [[headL]]. */
  def firstL[B >: A]: F[Option[B]] =
    stream.firstL

  /** Returns a new sequence that will take a maximum of
    * `n` elements from the start of the source sequence.
    */
  def take(n: Int): Self[A] =
    transform(_.take(n))

  /** Returns a new sequence that will take elements from
    * the start of the source sequence, for as long as the given
    * function `f` returns `true` and then stop.
    */
  def takeWhile(p: A => Boolean): Self[A] =
    transform(_.takeWhile(p))

  /** Recovers from potential errors by mapping them to other
    * async iterators using the provided function.
    */
  def onErrorHandleWith[B >: A](f: Throwable => Self[B]): Self[B] =
    transform(_.onErrorHandleWith(ex => f(ex).stream))

  /** Recovers from potential errors by mapping them to other
    * async iterators using the provided function.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Self[B]]): Self[B] =
    transform(_.onErrorRecoverWith { case ex if pf.isDefinedAt(ex) => pf(ex).stream })

  /** Recovers from potential errors by mapping them to
    * a final element using the provided function.
    */
  def onErrorHandle[B >: A](f: Throwable => B): Self[B] =
    transform(_.onErrorHandle(f))

  /** Recovers from potential errors by mapping them to
    * a final element using the provided function.
    */
  def onErrorRecover[B >: A](pf: PartialFunction[Throwable, B]): Self[B] =
    transform(_.onErrorRecover(pf))

  /** Drops the first `n` elements, from left to right. */
  def drop(n: Int): Self[A] =
    transform(_.drop(n))

  /** Triggers memoization of the iterable on the first traversal,
    * such that results will get reused on subsequent traversals.
    */
  def memoize: Self[A] =
    transform(_.memoize)

  /** Creates a new iterator that will consume the
    * source iterator and upon completion of the source it will
    * complete with `Unit`.
    */
  def completedL: F[Unit] =
    stream.completedL

  /** On evaluation it consumes the stream and for each element
    * execute the given function.
    */
  def foreachL(cb: A => Unit): F[Unit] =
    stream.foreachL(cb)
}

private[eval] abstract
class EnumeratorLikeBuilders[F[_], Self[+T] <: EnumeratorLike[T, F, Self]](implicit F: Evaluable[F]) {
  import Evaluable.ops._

  /** Lifts a [[ConsStream]] into an iterator. */
  def fromStream[A](stream: ConsStream[A,F]): Self[A]

  /** Lifts a strict value into an stream */
  def now[A](a: A): Self[A] =
    fromStream(ConsStream.now[A,F](a))

  /** Builder for an error state. */
  def error[A](ex: Throwable): Self[A] =
    fromStream(ConsStream.error[A,F](ex))

  /** Builder for an empty state. */
  def empty[A]: Self[A] =
    fromStream(ConsStream.empty[A,F])

  /** Builder for a wait iterator state. */
  def wait[A](rest: F[Self[A]]): Self[A] =
    fromStream(ConsStream.Wait[A,F](rest.map(_.stream)))

  /** Builds a next iterator state. */
  def next[A](head: A, rest: F[Self[A]]): Self[A] =
    fromStream(ConsStream.next[A,F](head, rest.map(_.stream)))

  /** Builds a next iterator state. */
  def nextSeq[A](headSeq: LinearSeq[A], rest: F[Self[A]]): Self[A] =
    fromStream(ConsStream.nextSeq[A,F](headSeq, rest.map(_.stream)))

  /** Lifts a strict value into an stream */
  def evalAlways[A](a: => A): Self[A] =
    fromStream(ConsStream.evalAlways(a))

  /** Lifts a strict value into an stream and
    * memoizes the result for subsequent executions.
    */
  def evalOnce[A](a: => A): Self[A] =
    fromStream(ConsStream.evalAlways[A,F](a))

  /** Promote a non-strict value representing a stream
    * to a stream of the same type.
    */
  def defer[A](fa: => Self[A]): Self[A] =
    fromStream(ConsStream.defer[A,F](fa.stream))

  /** Generates a range between `from` (inclusive) and `until` (exclusive),
    * with `step` as the increment.
    */
  def range(from: Long, until: Long, step: Long = 1L): Self[Long] =
    fromStream(ConsStream.range[F](from,until,step))

  /** Converts any sequence into an async iterable.
    *
    * Because the list is a linear sequence that's known
    * (but not necessarily strict), we'll just return
    * a strict state.
    */
  def fromList[A](list: immutable.LinearSeq[A], batchSize: Int): F[Self[A]] =
    ConsStream.fromList[A,F](list, batchSize).map(fromStream)

  /** Converts an iterable into an async iterator. */
  def fromIterable[A](iterable: Iterable[A], batchSize: Int): F[Self[A]] =
    ConsStream.fromIterable[A,F](iterable, batchSize).map(fromStream)

  /** Converts an iterable into an async iterator. */
  def fromIterable[A](iterable: java.lang.Iterable[A], batchSize: Int): F[Self[A]] =
    ConsStream.fromIterable[A,F](iterable, batchSize).map(fromStream)

  /** Converts a `scala.collection.Iterator` into an async iterator. */
  def fromIterator[A](iterator: scala.collection.Iterator[A], batchSize: Int): F[Self[A]] =
    ConsStream.fromIterator[A,F](iterator, batchSize).map(fromStream)

  /** Converts a `java.util.Iterator` into an async iterator. */
  def fromIterator[A](iterator: java.util.Iterator[A], batchSize: Int): F[Self[A]] =
    ConsStream.fromIterator[A,F](iterator, batchSize).map(fromStream)
}
