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

import monix.types.{Memoizable, MonadError}

/** A template for stream-like types based on [[Iterant]].
  *
  * Wraps an [[Iterant]] instance into a class type that has
  * a backed-in evaluation model, no longer exposing higher-kinded
  * types or type-class usage.
  *
  * This type is not meant for providing polymorphic behavior, but
  * for sharing implementation between types such as
  * [[TaskStream]] and [[CoevalStream]].
  *
  * @tparam A is the type of the elements emitted by the stream
  * @tparam Self is the type of the inheriting subtype
  * @tparam F is the monadic type that handles evaluation
  *         (e.g. [[monix.eval.Task Task]], [[monix.eval.Coeval Coeval]])
  */
abstract class IterantLike[+A, F[_], Self[+T] <: IterantLike[T, F, Self]]
  (implicit E: MonadError[F,Throwable], M: Memoizable[F]) {
  self: Self[A] =>

  import M.{applicative, functor, monad}

  /** Returns the underlying [[Iterant]] that handles this stream. */
  def stream: Iterant[F,A]

  /** Given a mapping function from one [[Iterant]] to another,
    * applies it and returns a new stream based on the result.
    *
    * Must be implemented by inheriting subtypes.
    */
  protected def transform[B](f: Iterant[F,A] => Iterant[F,B]): Self[B]

  /** Given a routine make sure to execute it whenever
    * the consumer executes the current `stop` action.
    */
  def doOnStop(f: F[Unit]): Self[A] =
    transform(_.doOnStop(f)(monad))

    /** Given a mapping function that returns a possibly lazy or asynchronous
    * result, applies it over the elements emitted by the stream.
    */
  final def mapEval[B](f: A => F[B]): Self[B] =
    transform(_.mapEval(f))

  /** Returns a new stream by mapping the supplied function
    * over the elements of the source.
    */
  final def map[B](f: A => B): Self[B] =
    transform(_.map(f))

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => Self[B]): Self[B] =
    transform(_.flatMap(a => f(a).stream)(M.monad))

  /** Appends the given stream to the end of the source,
    * effectively concatenating them.
    */
  final def ++[B >: A](rhs: Self[B]): Self[B] =
    transform(_ ++ rhs.stream)

  /** Returns a computation that should be evaluated in
    * case the streaming must be canceled before reaching
    * the end.
    *
    * This is useful to release any acquired resources,
    * like opened file handles or network sockets.
    */
  final def onCancel: F[Unit] =
    stream.onStop

  /** Left associative fold using the function 'f'.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  final def foldLeftL[S](seed: => S)(f: (S,A) => S): F[S] =
    stream.foldLeftL(seed)(f)

  /** Aggregates all elements in a `List` and preserves order. */
  final def toListL[B >: A]: F[List[B]] =
    stream.toListL[B]
}