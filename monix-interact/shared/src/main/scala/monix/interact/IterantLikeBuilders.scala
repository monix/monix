/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.interact

import monix.types.syntax._
import monix.types.{Memoizable, Monad, MonadError}
import scala.collection.immutable.LinearSeq

/** A template for companion objects of [[IterantLike]] subtypes.
  *
  * This type is not meant for providing polymorphic behavior, but
  * for sharing implementation between types such as
  * [[TaskStream]] and [[CoevalStream]].
  *
  * @tparam Self is the type of the inheriting subtype
  * @tparam F is the monadic type that handles evaluation in the
  *         [[Iterant]] implementation (e.g. [[monix.eval.Task Task]], [[monix.eval.Coeval Coeval]])
  *
  * @define fromIterableDesc Converts a `scala.collection.Iterable`
  *         into a stream.
  *
  *         Note that the generated `Iterator` is side-effectful and
  *         evaluating the `tail` references multiple times might yield
  *         undesired effects. So if you end up evaluating those `tail`
  *         references multiple times, consider using `memoize` on
  *         the resulting stream or apply `fromList` on an
  *         immutable `LinearSeq`.
  *
  * @define fromIteratorDesc Converts a `scala.collection.Iterator`
  *         into a stream.
  *
  *         Note that the generated `Iterator` is side-effectful and
  *         evaluating the `tail` references multiple times might yield
  *         undesired effects. So if you end up evaluating those `tail`
  *         references multiple times, consider using `memoize` on
  *         the resulting stream or apply `fromList` on an
  *         immutable `LinearSeq`.
  *
  * @define nextDesc Builds a stream equivalent with [[Iterant.Next]],
  *         a pairing between a `head` and a potentially lazy or
  *         asynchronous `tail`.
  *
  *         @see [[Iterant.Next]]
  *
  * @define nextSeqDesc Builds a sequence equivalent with [[Iterant.NextSeq]].
  *
  *         This [[monix.interact.Iterant.NextSeq NextSeq]] state
  *         of the [[Iterant]] represents a `cursor` / `rest` cons pair,
  *         where the `cursor` is an [[Cursor iterator-like]] type that
  *         can generate a whole batch of elements.
  *
  *         Useful for doing buffering, or by giving it an empty cursor,
  *         useful to postpone the evaluation of the next element.
  *
  * @define suspendDesc Builds a stream equivalent with [[Iterant.Suspend]],
  *         representing a suspended stream, useful for delaying its
  *         initialization, the evaluation being controlled by the
  *         underlying monadic context (e.g. [[monix.eval.Task Task]], [[monix.eval.Coeval Coeval]], etc).
  *
  *         @see [[Iterant.Suspend]]
  *
  * @define haltDesc Builds an empty stream that can potentially signal an
  *         error.
  *
  *         Used as a final node of a stream (the equivalent of Scala's `Nil`),
  *         wrapping a [[Iterant.Halt]] instance.
  *
  *         @see [[Iterant.Halt]]
  *
  * @define stopDesc is a computation to be executed in case
  *         streaming is stopped prematurely, giving it a chance
  *         to do resource cleanup (e.g. close file handles)
  *
  * @define batchSizeDesc indicates the size of a streamed batch
  *         on each event (generating [[Iterant.NextSeq]] nodes) or no
  *         batching done if it is equal to 1
  */
abstract class IterantLikeBuilders[F[_], Self[+T] <: IterantLike[T, F, Self]]
  (implicit E: MonadError[F,Throwable], M: Memoizable[F]) { self =>

  import M.{applicative, functor}

  /** Materializes a [[Iterant]]. */
  def fromStream[A](stream: Iterant[F,A]): Self[A]

  /** Given a sequence of elements, builds a stream out of it. */
  def apply[A](elems: A*): Self[A] =
    fromStream(Iterant.apply[F,A](elems:_*))

  /** Lifts a strict value into the stream context,
    * returning a stream of one element.
    */
  def now[A](a: A): Self[A] =
    fromStream(Iterant.now[F,A](a))

  /** Alias for [[now]]. */
  def pure[A](a: A): Self[A] =
    fromStream(Iterant.pure[F,A](a))

  /** Lifts a non-strict value into the stream context,
    * returning a stream of one element that is lazily
    * evaluated.
    */
  def eval[A](a: => A): Self[A] =
    fromStream(Iterant.eval[F,A](a))

  /** $nextDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def nextS[A](head: A, tail: F[Self[A]], stop: F[Unit]): Self[A] =
    fromStream(Iterant.nextS[F,A](head, functor.map(tail)(_.stream), stop))

  /** $nextSeqDesc
    *
    * @param cursor is an [[Cursor iterator-like]] type that can generate
    *        elements by traversing a collection
    * @param rest is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def nextSeqS[A](cursor: Cursor[A], rest: F[Self[A]], stop: F[Unit]): Self[A] =
    fromStream(Iterant.nextSeqS[F,A](cursor, functor.map(rest)(_.stream), stop))

  /** $suspendDesc
    *
    * @param rest is the suspended stream
    * @param stop $stopDesc
    */
  def suspendS[A](rest: F[Self[A]], stop: F[Unit]): Self[A] =
    fromStream(Iterant.suspendS[F,A](rest.map(_.stream), stop))

  /** $haltDesc */
  def haltS[A](ex: Option[Throwable]): Self[A] =
    fromStream(Iterant.haltS[F,A](ex))

  /** Promote a non-strict value representing a stream to a stream
    * of the same type, effectively delaying its initialisation.
    *
    * The suspension will act as a factory of streams, with any
    * described side-effects happening on each evaluation.
    */
  def suspend[A](fa: => Self[A]): Self[A] =
    fromStream(Iterant.suspend[F,A](fa.stream))

  /** Alias for [[IterantLikeBuilders.suspend[A](fa* suspend]]. */
  def defer[A](fa: => Self[A]): Self[A] =
    fromStream(Iterant.defer[F,A](fa.stream))

  /** $suspendDesc
    *
    * @param rest is the suspended stream
    */
  def suspend[A](rest: F[Self[A]]): Self[A] =
    fromStream(Iterant.suspend[F,A](rest.map(_.stream)))

  /** Returns an empty stream. */
  def empty[A]: Self[A] = fromStream(Iterant.empty[F,A])

  /** Returns a stream that signals an error. */
  def raiseError[A](ex: Throwable): Self[A] =
    fromStream(Iterant.raiseError[F,A](ex))

  /** Converts any Scala `collection.IndexedSeq` into a stream.
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIndexedSeq[A](xs: IndexedSeq[A]): Self[A] =
    fromStream(Iterant.fromIndexedSeq[F,A](xs))

  /** Converts any Scala `collection.immutable.LinearSeq`
    * into a stream.
    */
  def fromList[A](list: LinearSeq[A]): Self[A] =
    fromStream(Iterant.fromList[F,A](list))

  /** Converts a `scala.collection.immutable.Seq` into a stream. */
  def fromSeq[A](seq: Seq[A]): Self[A] =
    fromStream(Iterant.fromSeq[F,A](seq))

  /** $fromIterableDesc
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIterable[A](xs: Iterable[A]): Self[A] =
    fromStream(Iterant.fromIterable[F,A](xs))

  /** $fromIteratorDesc
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIterator[A](xs: Iterator[A]): Self[A] =
    fromStream(Iterant.fromIterator[F,A](xs))

  /** Type-class instances for [[IterantLike]] types. */
  implicit val typeClassInstances: TypeClassInstances =
    new TypeClassInstances

  /** Type-class instances for [[IterantLike]] types. */
  class TypeClassInstances extends Monad.Instance[Self] {
    override def pure[A](a: A): Self[A] =
      self.pure(a)
    override def eval[A](a: => A): Self[A] =
      self.eval(a)
    override def suspend[A](fa: => Self[A]): Self[A] =
      self.suspend(fa)
    override def map[A, B](fa: Self[A])(f: (A) => B): Self[B] =
      fa.map(f)
    override def map2[A, B, Z](fa: Self[A], fb: Self[B])(f: (A, B) => Z): Self[Z] =
      for (a <- fa; b <- fb) yield f(a,b)
    override def ap[A, B](ff: Self[(A) => B])(fa: Self[A]): Self[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def flatMap[A, B](fa: Self[A])(f: (A) => Self[B]): Self[B] =
      fa.flatMap(f)
  }
}