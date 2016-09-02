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

import monix.eval.Stream._
import monix.types._

import scala.collection.{LinearSeq, mutable}
import scala.util.control.NonFatal

/** The `Stream` is a type that describes lazy, possibly asynchronous
  * streaming of elements
  *
  * It is similar somewhat in spirit to Scala's own
  * `collection.immutable.Stream` and with Java's `Iterable`, except
  * that it is more composable and more flexible due to evaluation being
  * controlled by an `F[_]` monadic type that you have to supply
  * (like [[Task]] or [[Coeval]]) which will control the evaluation.
  * In other words, this `Stream` type is capable of strict or lazy,
  * synchronous or asynchronous evaluation.
  *
  * Consumption of a `Stream` happens typically in a loop where
  * the current step represents either a signal that the stream
  * is over, or a head/tail pair.
  *
  * The type is an ADT, meaning a composite of the following types:
  *
  *  - [[monix.eval.Stream.Next Next]] which signals a single strict
  *    element, the `head` and a `tail` representing the rest of the stream
  *  - [[monix.eval.Stream.NextF NextF]] is a variation on `Next`
  *    for signaling a lazily computed `head` and the `tail`
  *    representing the rest of the stream
  *  - [[monix.eval.Stream.NextSeq NextSeq]] signaling a
  *    whole batch of elements, as an optimization, along
  *    with the `tail` that represents the rest of the stream
  *  - [[monix.eval.Stream.Wait Wait]] is for suspending the
  *    evaluation of a stream, very much similar with a `NextSeq`
  *    that has its `headSeq` empty
  *  - [[monix.eval.Stream.Halt Halt]] represents an empty
  *    stream, signaling the end, either in success or in error
  *
  * The `Stream` type accepts as type parameter an `F` monadic type that
  * is used to control how evaluation happens. For example you can
  * use [[Task]], in which case the streaming can have asynchronous
  * behavior, or you can use [[Coeval]] in which case it can behave
  * like a normal, synchronous `Iterable`.
  *
  * As restriction, this type used must be stack safe in `map` and `flatMap`.
  *
  * ATTRIBUTION: this type was inspired by the `Streaming` type in the
  * Typelevel Cats library (later moved to Typelevel's Dogs :)), originally
  * committed in Cats by Erik Osheim. Several operations from `Streaming`
  * were adapted for this `Stream` type, like `flatMap`, `foldRightL`
  * and `zipMap`.
  *
  * @tparam F is the monadic type that controls evaluation; note that it
  *         must be stack-safe in its `map` and `flatMap` operations
  *
  * @tparam A is the type of the elements produced by this Stream
  */
sealed trait Stream[F[_], +A] extends Product with Serializable { self =>
  /** Returns a computation that should be evaluated in
    * case the streaming must be canceled before reaching
    * the end.
    *
    * This is useful to release any acquired resources,
    * like opened file handles or network sockets.
    */
  def onCancel(implicit F: Applicative[F]): F[Unit] =
    this match {
      case Next(_, _, ref) => ref
      case NextF(_, _, ref) => ref
      case NextSeq(_, _, ref) => ref
      case Wait(_, ref) => ref
      case Halt(_) => F.unit
    }

  /** Left associative fold using the function 'f'.
    *
    * On execution the iterable will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  final def foldLeftL[S](seed: => S)(f: (S,A) => S)
    (implicit D: Deferrable[F], ME: MonadError[F,Throwable]): F[S] = {

    def loop(self: Stream[F,A], state: S)(implicit A: Applicative[F], M: Monad[F]): F[S] = {
      def next(a: A, next: F[Stream[F,A]], cancel: F[Unit]): F[S] =
        try {
          val newState = f(state, a)
          M.flatMap(next)(loop(_, newState))
        } catch {
          case NonFatal(ex) =>
            M.flatMap(cancel)(_ => ME.raiseError(ex))
        }

      self match {
        case Halt(None) =>
          A.pure(state)
        case Halt(Some(ex)) =>
          ME.raiseError(ex)
        case Next(a, tail, cancel) =>
          next(a, tail, cancel)
        case NextF(headF, tail, cancel) =>
          M.flatMap(headF)(a => next(a, tail, cancel))
        case Wait(rest, _) =>
          M.flatMap(rest)(loop(_, state))
        case NextSeq(list, next, _) =>
          if (list.isEmpty)
            M.flatMap(next)(loop(_, state))
          else try {
            val newState = list.foldLeft(state)(f)
            M.flatMap(next)(loop(_, newState))
          } catch {
            case NonFatal(ex) =>
              M.flatMap(self.onCancel)(_ => ME.raiseError(ex))
          }
      }
    }

    implicit val A = D.applicative
    implicit val M = D.monad

    val init = ME.onErrorHandleWith(D.eval(seed))(ex => M.flatMap(onCancel)(_ => ME.raiseError(ex)))
    M.flatMap(init)(a => loop(self, a))
  }
}

/**
  * @define nextDesc The [[monix.eval.Stream.Next Next]] state
  *         of the [[Stream]] represents a `head` / `tail`
  *         cons pair.
  *
  *         Note the `head` is a strict value, whereas the `tail` is
  *         meant to be lazy and can have asynchronous behavior as well,
  *         depending on the `F` type used. Also see
  *         [[monix.eval.Stream.NextF NextF]] for a state that
  *         yields a possibly lazy `head`, or
  *         [[monix.eval.Stream.NextSeq NextSeq]] for a
  *         state that yields a whole sequence.
  *
  * @define nextSeqDesc The [[monix.eval.Stream.NextSeq NextSeq]] state
  *         of the [[Stream]] represents a `headSeq` / `tail` cons pair.
  *
  *         Like [[monix.eval.Stream.Next Next]] except that
  *         the head is a strict sequence of elements that don't need
  *         asynchronous execution. The `headSeq` sequence can be
  *         empty. Useful for doing buffering.
  *
  * @define waitDesc The [[monix.eval.Stream.Wait Wait]] state
  *         of the [[Stream]] represents a
  *
  * @define nextFDesc The [[monix.eval.Stream.NextF NextF]] state
  *         of the [[Stream]] represents a `head` / `tail` cons pair,
  *         where the `head` is a possibly lazy or asynchronous
  *         value, its evaluation model depending on `F`.
  *
  *         See [[monix.eval.Stream.Next Next]] for the a state where
  *         the head is a strict value or [[monix.eval.Stream.NextSeq NextSeq]]
  *         for a state in which the head is a strict list.
  *
  * @define waitDesc The [[monix.eval.Stream.Wait Wait]] state
  *         of the [[Stream]] represents a suspended stream to be
  *         evaluated in the `F` context. It is useful to delay the
  *         evaluation of a stream by deferring to `F`.
  *
  * @define cancelDesc is a computation to be executed in case
  *         streaming is stopped prematurely, giving it a chance
  *         to do resource cleanup (e.g. close file handles)
  *
  * @define haltDesc The [[monix.eval.Stream.Halt Halt]] state
  *         of the [[Stream]] represents the completion state
  *         of a stream, with an optional exception if an error
  *         happened.
  *
  *         `Halt` is received as a final state in the iteration process.
  *         This state cannot be followed by any other element and
  *         represents the end of the stream.
  */
object Stream {
  /** Converts an iterable into a stream. */
  def fromIterable[F[_],A](iterable: Iterable[A], batchSize: Int)
    (implicit M: Monad[F], F: Deferrable[F]): Stream[F,A] = {

    val init = F.eval(iterable.iterator)
    val cancel = F.applicative.unit
    val rest = M.flatMap(init)(iterator => fromIterator(iterator, batchSize))
    Wait[F,A](rest, cancel)
  }

  /** Converts a `java.util.Iterator` into an async iterator. */
  def fromIterator[F[_], A](iterator: Iterator[A], batchSize: Int)
    (implicit F: Deferrable[F]): F[Stream[F,A]] = {

    F.eval {
      try {
        val A = F.applicative
        val buffer = mutable.ListBuffer.empty[A]
        var processed = 0
        while (processed < batchSize && iterator.hasNext) {
          buffer += iterator.next()
          processed += 1
        }

        if (processed == 0)
          Halt(None)
        else if (processed == 1)
          Next[F,A](buffer.head, fromIterator(iterator, batchSize), A.unit)
        else
          NextSeq[F,A](buffer.toList, fromIterator(iterator, batchSize), A.unit)
      } catch {
        case NonFatal(ex) =>
          Halt(Some(ex))
      }
    }
  }

  /** A template for stream-like types based on [[Stream]].
    *
    * Wraps a [[Stream]] instance into a class type that has
    * a backed-in evaluation model, no longer exposing higher-kinded
    * types or type-class usage.
    *
    * This type is not meant for providing polymorphic behavior, but
    * for sharing implementation between types such as
    * [[TaskStream]] and [[CoevalStream]].
    *
    * @tparam A is the type of the elements emitted by the stream
    * @tparam Self is the type of the inheriting subtype
    * @tparam F is the monadic type that handles evaluation in the
    *         [[Stream]] implementation (e.g. [[Task]], [[Coeval]])
    */
  abstract class Like[+A, F[_], Self[+T] <: Like[T, F, Self]] extends Serializable {
    self: Self[A] with MonadError.Type[F,Throwable] with Memoizable.Type[F] =>

    /** Returns the underlying [[Stream]]. */
    def stream: Stream[F,A]

    /** Given a mapping function from one [[Stream]] to another,
      * applies it and returns a new stream based on the result.
      *
      * Must be implemented by inheriting subtypes.
      */
    protected def transform[B](f: Stream[F,A] => Stream[F,B]): Self[B]

    /** Returns a computation that should be evaluated in
      * case the stream must be canceled before reaching
      * the end.
      *
      * This is useful to release any acquired resources,
      * like opened file handles or network sockets.
      */
    final def cancel: F[Unit] =
      stream.onCancel

    /** Left associative fold using the function 'f'.
      *
      * On execution the iterable will be traversed from left to right,
      * and the given function will be called with the prior result,
      * accumulating state until the end, when the summary is returned.
      */
    final def foldLeftL[S](seed: => S)(f: (S,A) => S): F[S] =
      stream.foldLeftL(seed)(f)
  }

  /** $nextDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  final case class Next[F[_],A](
    head: A,
    tail: F[Stream[F,A]],
    cancel: F[Unit])
    extends Stream[F,A]

  /** $nextFDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  final case class NextF[F[_],A](
    head: F[A],
    tail: F[Stream[F,A]],
    cancel: F[Unit])
    extends Stream[F,A]

  /** $nextSeqDesc
    *
    * @param headSeq is a sequence with the next elements to be processed; can be empty
    * @param tail is the rest of the stream
    * @param cancel $cancelDesc
    */
  final case class NextSeq[F[_],A](
    headSeq: LinearSeq[A],
    tail: F[Stream[F,A]],
    cancel: F[Unit])
    extends Stream[F,A]

  /** $waitDesc
    *
    * @param rest is the suspended stream
    * @param cancel $cancelDesc
    */
  final case class Wait[F[_], A](
    rest: F[Stream[F,A]],
    cancel: F[Unit])
    extends Stream[F,A]

  /** $haltDesc
    *
    * @param ex is an optional exception that, in case it is
    *        present, it means that the streaming ended in error.
    */
  final case class Halt[F[_]](ex: Option[Throwable])
    extends Stream[F,Nothing]
}
