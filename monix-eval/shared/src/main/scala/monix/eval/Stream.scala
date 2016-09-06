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
import monix.execution.internal.Platform
import monix.types._
import monix.types.syntax._
import scala.collection.immutable.{LinearSeq, Seq}
import scala.collection.mutable
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
  *  - [[monix.eval.Stream.Cons Cons]] which signals a single strict
  *    element, the `head` and a `tail` representing the rest of the stream
  *  - [[monix.eval.Stream.ConsLazy ConsLazy]] is a variation on `Cons`
  *    for signaling a lazily computed `head` and the `tail`
  *    representing the rest of the stream
  *  - [[monix.eval.Stream.ConsSeq ConsSeq]] is a variation on `Cons`
  *    for signaling a whole strict batch of elements as the `head`,
  *    along with the `tail` representing the rest of the stream
  *  - [[monix.eval.Stream.Wait Wait]] is for suspending the
  *    evaluation of a stream
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
  final def onCancel(implicit F: Applicative[F]): F[Unit] =
    this match {
      case Cons(_, _, ref) => ref
      case ConsLazy(_, _, ref) => ref
      case ConsSeq(_, _, ref) => ref
      case Wait(_, ref) => ref
      case Halt(_) => F.unit
    }

  /** Given a mapping function that returns a possibly lazy or asynchronous
    * result, applies it over the elements emitted by the stream.
    */
  final def mapEval[B](f: A => F[B])(implicit F: Monad[F]): Stream[F, B] = {
    import F.{applicative, functor}

    this match {
      case Cons(head, tail, cancel) =>
        try ConsLazy[F,B](f(head), tail.map(_.mapEval(f)), cancel)
        catch { case NonFatal(ex) => signalError(ex) }

      case ConsLazy(headF, tail, cancel) =>
        try ConsLazy[F,B](F.flatMap(headF)(f), tail.map(_.mapEval(f)), cancel)
        catch { case NonFatal(ex) => signalError(ex) }

      case ConsSeq(headSeq, tailF, cancel) =>
        if (headSeq.isEmpty)
          Wait[F,B](tailF.map(_.mapEval(f)), cancel)
        else {
          val head = headSeq.head
          val tail = F.applicative.pure(ConsSeq(headSeq.tail, tailF, cancel))

          try ConsLazy[F,B](f(head), tail.map(_.mapEval(f)), cancel)
          catch { case NonFatal(ex) => signalError(ex) }
        }

      case Wait(rest, cancel) =>
        Wait[F,B](F.functor.map(rest)(_.mapEval(f)), cancel)
      case halt @ Halt(_) =>
        halt
    }
  }

  /** Left associative fold using the function 'f'.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  final def foldLeftL[S](seed: => S)(f: (S,A) => S)
    (implicit F: MonadEval[F], E: MonadError[F,Throwable]): F[S] = {

    def loop(self: Stream[F,A], state: S)(implicit A: Applicative[F], M: Monad[F]): F[S] = {
      def next(a: A, next: F[Stream[F,A]], cancel: F[Unit]): F[S] =
        try {
          val newState = f(state, a)
          next.flatMap(loop(_, newState))
        } catch {
          case NonFatal(ex) =>
            cancel.flatMap(_ => E.raiseError(ex))
        }

      self match {
        case Halt(None) =>
          A.pure(state)
        case Halt(Some(ex)) =>
          E.raiseError(ex)
        case Cons(a, tail, cancel) =>
          next(a, tail, cancel)
        case ConsLazy(headF, tail, cancel) =>
          // Also protecting the head!
          val head = headF.onErrorHandleWith(ex => cancel.flatMap(_ => E.raiseError(ex)))
          head.flatMap(a => next(a, tail, cancel))
        case Wait(rest, _) =>
          rest.flatMap(loop(_, state))
        case ConsSeq(list, next, cancel) =>
          if (list.isEmpty)
            next.flatMap(loop(_, state))
          else try {
            val newState = list.foldLeft(state)(f)
            next.flatMap(loop(_, newState))
          } catch {
            case NonFatal(ex) =>
              cancel.flatMap(_ => E.raiseError(ex))
          }
      }
    }

    implicit val A = F.applicative
    implicit val M = F.monad

    val init = F.eval(seed).onErrorHandleWith(ex => onCancel.flatMap(_ => E.raiseError(ex)))
    init.flatMap(a => loop(self, a))
  }

  /** Aggregates all elements in a `List` and preserves order. */
  final def toListL[B >: A](implicit F: MonadEval[F], E: MonadError[F,Throwable]): F[List[B]] = {
    val folded = foldLeftL(mutable.ListBuffer.empty[B]) { (acc, a) => acc += a }
    F.functor.map(folded)(_.toList)
  }

  private def signalError(ex: Throwable)(implicit F: Applicative[F]): Stream[F, Nothing] = {
    val c = onCancel
    val t: F[Stream[F,Nothing]] = F.functor.map(c)(_ => Stream.Halt[F](Some(ex)))
    Stream.Wait[F,Nothing](t, c)
  }
}

/**
  * @define consDesc The [[monix.eval.Stream.Cons Cons]] state
  *         of the [[Stream]] represents a `head` / `tail`
  *         cons pair, where the `head` is a strict value.
  *
  *         Note the `head` being a strict value means that it is
  *         already known, whereas the `tail` is meant to be lazy and
  *         can have asynchronous behavior as well, depending on the `F`
  *         type used.
  *
  *         See [[monix.eval.Stream.ConsLazy ConsLazy]] for
  *         a state that yields a possibly lazy `head`.
  *         See [[monix.eval.Stream.ConsSeq ConsSeq]]
  *         for a state where the head is a strict immutable list.
  *
  * @define consLazyDesc The [[monix.eval.Stream.ConsLazy ConsLazy]] state
  *         of the [[Stream]] represents a `head` / `tail` cons pair,
  *         where the `head` is a possibly lazy or asynchronous
  *         value, its evaluation model depending on `F`.
  *
  *         See [[monix.eval.Stream.Cons Cons]] for the a state where
  *         the head is a strict value. See [[monix.eval.Stream.ConsSeq ConsSeq]]
  *         for a state where the head is a strict immutable list.
  *
  * @define consSeqDesc The [[monix.eval.Stream.ConsSeq ConsSeq]] state
  *         of the [[Stream]] represents a `head` / `tail` cons pair,
  *         where the `head` is a strict list of elements.
  *
  *         The `head` is a Scala `collection.immutable.LinearSeq` and so
  *         it accepts collections that are guaranteed to be immutable and
  *         that have a reasonably efficient head/tail decomposition, examples
  *         being Scala's `List`, `Queue` and `Stack`. The `head` can be
  *         empty.
  *
  *         Useful for doing buffering, or by giving it an empty list,
  *         useful to postpone the evaluation of the next element.
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
  *
  * @define fromIteratorDesc Converts a `scala.collection.Iterator`
  *         into a stream.
  *
  *         Note that an `Iterator` is side-effectful and evaluating the
  *         `tail` references multiple times might yield undesired effects.
  *         Therefore, in order for evaluation of the resulting stream
  *         to be free of surprises, the result of the evaluation driven
  *         by the `F` context is memoized (see [[monix.types.Memoizable]]).
  *
  *         But memoization implies a slight overhead, so for a much cheaper
  *         conversion from a Scala list see `fromList`.
  *
  * @define fromIterableDesc Converts a `scala.collection.Iterable`
  *         into a stream.
  *
  *         Note that an `Iterable` reference eventually yields an `Iterator`
  *         that is side-effectful and evaluating the `tail` references multiple
  *         times might yield undesired effects. Therefore, in order for
  *         evaluation of the resulting stream to be free of surprises,
  *         the result of the evaluation driven by the `F` context is
  *         memoized (see [[monix.types.Memoizable]]).
  *
  *         But memoization implies a slight overhead, so for a much cheaper
  *         conversion from a Scala list see `fromList`.
  *
  * @define batchSizeDesc indicates the size of a streamed batch on each event
  *        (by means of [[Stream.ConsSeq]]) or no batching done if it is
  *        equal to 1
  */
object Stream {
  /** Given a sequence of elements, builds a stream out of it. */
  def apply[F[_] : Applicative, A](elems: A*): Stream[F,A] =
    fromList[F,A](elems.toList)

  /** Builds a [[Stream.Cons]] stream state.
    *
    * $consDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  def cons[F[_],A](head: A, tail: F[Stream[F,A]], cancel: F[Unit]): Stream[F,A] =
    Cons[F,A](head, tail, cancel)

  /** Builds a [[Stream.Cons]] stream state.
    *
    * $consDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    */
  def cons[F[_],A](head: A, tail: F[Stream[F,A]])
    (implicit F: Applicative[F]): Stream[F,A] =
    cons[F,A](head, tail, F.unit)

  /** Builds a [[Stream.ConsLazy]] stream state.
    *
    * $consLazyDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  def consLazy[F[_],A](head: F[A], tail: F[Stream[F,A]], cancel: F[Unit]): Stream[F,A] =
    ConsLazy[F,A](head, tail, cancel)

  /** Builds a [[Stream.ConsLazy]] stream state.
    *
    * $consLazyDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    */
  def consLazy[F[_],A](head: F[A], tail: F[Stream[F,A]])
    (implicit F: Applicative[F]): Stream[F,A] =
    consLazy[F,A](head, tail, F.unit)

  /** Builds a [[Stream.ConsSeq]] stream state.
    *
    * $consSeqDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  def consSeq[F[_],A](head: LinearSeq[A], tail: F[Stream[F,A]], cancel: F[Unit]): Stream[F,A] =
    ConsSeq[F,A](head, tail, cancel)

  /** Builds a [[Stream.ConsSeq]] stream state.
    *
    * $consSeqDesc
    *
    * @param head is a strict list of the next elements to be processed, can be empty
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    */
  def consSeq[F[_],A](head: LinearSeq[A], tail: F[Stream[F,A]])
    (implicit F: Applicative[F]): Stream[F,A] =
    consSeq[F,A](head, tail, F.unit)

  /** Builds a [[Stream.Wait]] stream state.
    *
    * $waitDesc
    *
    * @param rest is the suspended stream
    * @param cancel $cancelDesc
    */
  def wait[F[_],A](rest: F[Stream[F,A]], cancel: F[Unit]): Stream[F,A] =
    Wait[F,A](rest, cancel)

  /** Builds a [[Stream.Wait]] stream state.
    *
    * $waitDesc
    *
    * @param rest is the suspended stream
    */
  def wait[F[_],A](rest: F[Stream[F,A]])
    (implicit F: Applicative[F]): Stream[F,A] =
    wait[F,A](rest, F.unit)

  /** Returns an empty stream. */
  def empty[F[_],A]: Stream[F,A] =
    Halt[F](None)

  /** Returns a stream that signals an error. */
  def raiseError[F[_],A](ex: Throwable): Stream[F,A] =
    Halt[F](Some(ex))

  /** Builds a [[Stream.Halt]] stream state.
    *
    * $haltDesc
    */
  def halt[F[_],A](ex: Option[Throwable]): Stream[F,A] =
    Halt[F](ex)

  /** Converts any Scala `collection.immutable.LinearSeq`
    * into a stream.
    */
  def fromList[F[_], A](list: LinearSeq[A])(implicit F: Applicative[F]): Stream[F,A] =
    ConsSeq[F,A](list, F.pure(halt[F,A](None)), F.unit)

  /** Converts a `scala.collection.immutable.Seq` into a stream. */
  def fromSeq[F[_], A](seq: Seq[A])(implicit F: Monad[F]): Stream[F,A] = {
    def loop(seq: Seq[A]): Stream[F,A] = {
      val (head, tail) = seq.splitAt(Platform.recommendedBatchSize)

      if (head.isEmpty)
        Halt[F](None)
      else if (tail.isEmpty) {
        val empty = F.applicative.pure(Stream.halt[F,A](None))
        ConsSeq[F,A](head.toList, empty, F.applicative.unit)
      }
      else {
        val next = F.applicative.pure(tail)
        ConsSeq[F,A](head.toList, F.functor.map(next)(loop),
          F.applicative.unit)
      }
    }

    seq match {
      case ref: LinearSeq[_] =>
        fromList[F,A](ref.asInstanceOf[LinearSeq[A]])(F.applicative)
      case _ =>
        loop(seq)
    }
  }

  /** $fromIterableDesc
    *
    * @param iterable is the reference to be converted to a stream
    */
  def fromIterable[F[_] : Memoizable, A](iterable: Iterable[A]): Stream[F,A] =
    fromIterable[F,A](iterable, 1)

  /** $fromIterableDesc
    *
    * @param iterable is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIterable[F[_],A](iterable: Iterable[A], batchSize: Int)
    (implicit F: Memoizable[F]): Stream[F,A] = {

    require(batchSize > 0, "batchSize should be strictly positive")
    val init = F.monadEval.eval(iterable.iterator)
    val cancel = F.applicative.unit
    val rest = F.functor.map(init)(iterator => fromIterator[F,A](iterator, batchSize))
    Wait[F,A](rest, cancel)
  }

  /** $fromIteratorDesc
    *
    * @param iterator is the reference to be converted to a stream
    */
  def fromIterator[F[_] : Memoizable, A](iterator: Iterator[A]): Stream[F,A] =
    fromIterator[F,A](iterator, batchSize = 1)

  /** $fromIteratorDesc
    *
    * @param iterator is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIterator[F[_], A](iterator: Iterator[A], batchSize: Int)
    (implicit F: Memoizable[F]): Stream[F,A] = {

    def loop(): F[Stream[F,A]] =
      F.evalOnce {
        try {
          val buffer = mutable.ListBuffer.empty[A]
          var processed = 0
          while (processed < batchSize && iterator.hasNext) {
            buffer += iterator.next()
            processed += 1
          }

          if (processed == 0) Halt(None)
          else if (processed == 1)
            Cons[F,A](buffer.head, loop(), F.applicative.unit)
          else
            ConsSeq[F,A](buffer.toList, loop(), F.applicative.unit)
        } catch {
          case NonFatal(ex) =>
            Halt(Some(ex))
        }
      }

    require(batchSize > 0, "batchSize should be strictly positive")
    Wait[F,A](loop(), F.applicative.unit)
  }

  /** $consDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  final case class Cons[F[_],A](
    head: A,
    tail: F[Stream[F,A]],
    cancel: F[Unit])
    extends Stream[F,A]

  /** $consLazyDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param cancel $cancelDesc
    */
  final case class ConsLazy[F[_],A](
    head: F[A],
    tail: F[Stream[F,A]],
    cancel: F[Unit])
    extends Stream[F,A]

  /** $consSeqDesc
    *
    * @param head is a strict list of the next elements to be processed, can be empty
    * @param tail is the rest of the stream
    * @param cancel $cancelDesc
    */
  final case class ConsSeq[F[_],A](
    head: LinearSeq[A],
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

  /** A template for stream-like types based on [[Stream]].
    *
    * Wraps an [[Stream]] instance into a class type that has
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
    *         (e.g. [[Task]], [[Coeval]])
    */
  abstract class Like[+A, F[_], Self[+T] <: Like[T, F, Self]]
    (implicit E: MonadError[F,Throwable], M: Memoizable[F]) {
    self: Self[A] =>

    import M.{applicative, monadEval}

    /** Returns the underlying [[Stream]] that handles this stream. */
    def stream: Stream[F,A]

    /** Given a mapping function from one [[Stream]] to another,
      * applies it and returns a new stream based on the result.
      *
      * Must be implemented by inheriting subtypes.
      */
    protected def transform[B](f: Stream[F,A] => Stream[F,B]): Self[B]

    /** Given a mapping function that returns a possibly lazy or asynchronous
      * result, applies it over the elements emitted by the stream.
      */
    final def mapEval[B](f: A => F[B]): Self[B] =
      transform(_.mapEval(f)(M.monad))

    /** Returns a computation that should be evaluated in
      * case the streaming must be canceled before reaching
      * the end.
      *
      * This is useful to release any acquired resources,
      * like opened file handles or network sockets.
      */
    final def onCancel: F[Unit] =
      stream.onCancel

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

  /** A template for companion objects of [[Stream.Like]] subtypes.
    *
    * This type is not meant for providing polymorphic behavior, but
    * for sharing implementation between types such as
    * [[TaskStream]] and [[CoevalStream]].
    *
    * @tparam Self is the type of the inheriting subtype
    * @tparam F is the monadic type that handles evaluation in the
    *         [[Stream]] implementation (e.g. [[Task]], [[Coeval]])
    */
  abstract class Builders[F[_], Self[+T] <: Like[T, F, Self]]
    (implicit E: MonadError[F,Throwable], M: Memoizable[F]) {

    import M.{applicative, functor, monad}

    /** Materializes a [[Stream]]. */
    def fromStream[A](stream: Stream[F,A]): Self[A]

    /** Given a sequence of elements, builds a stream out of it. */
    def apply[A](elems: A*): Self[A] =
      fromStream(Stream.apply[F,A](elems:_*))

    /** Builds a stream equivalent with the [[Stream.Cons]]
      * stream state.
      *
      * $consDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def cons[A](head: A, tail: F[Self[A]]): Self[A] =
      fromStream(Stream.cons[F,A](head, functor.map(tail)(_.stream)))

    /** Builds a stream equivalent with the [[Stream.Cons]]
      * stream state.
      *
      * $consDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param cancel $cancelDesc
      */
    def cons[A](head: A, tail: F[Self[A]], cancel: F[Unit]): Self[A] =
      fromStream(Stream.cons[F,A](head, functor.map(tail)(_.stream), cancel))

    /** Builds a stream equivalent with the [[Stream.ConsLazy]]
      * stream state.
      *
      * $consLazyDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def consLazy[A](head: F[A], tail: F[Self[A]]): Self[A] =
      fromStream(Stream.consLazy[F,A](head, functor.map(tail)(_.stream)))

    /** Builds a stream equivalent with the [[Stream.ConsLazy]]
      * stream state.
      *
      * $consLazyDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param cancel $cancelDesc
      */
    def consLazy[A](head: F[A], tail: F[Self[A]], cancel: F[Unit]): Self[A] =
      fromStream(Stream.consLazy[F,A](head, functor.map(tail)(_.stream), cancel))

    /** Builds a stream equivalent with the [[Stream.ConsSeq]]
      * stream state.
      *
      * $consSeqDesc
      *
      * @param head is a strict list of the next elements to be processed, can be empty
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def consSeq[A](head: LinearSeq[A], tail: F[Self[A]]): Self[A] =
      fromStream(Stream.consSeq[F,A](head, functor.map(tail)(_.stream)))

    /** Builds a stream equivalent with the [[Stream.ConsSeq]]
      * stream state.
      *
      * $consSeqDesc
      *
      * @param head is a strict list of the next elements to be processed, can be empty
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param cancel $cancelDesc
      */
    def consSeq[A](head: LinearSeq[A], tail: F[Self[A]], cancel: F[Unit]): Self[A] =
      fromStream(Stream.consSeq[F,A](head, functor.map(tail)(_.stream), cancel))

    /** Builds a stream equivalent with the [[Stream.Wait]]
      * stream state.
      *
      * $waitDesc
      *
      * @param rest is the suspended stream
      */
    def wait[A](rest: F[Stream[F,A]]): Self[A] =
      fromStream(Stream.wait[F,A](rest))

    /** Builds a stream equivalent with the [[Stream.Wait]]
      * stream state.
      *
      * $waitDesc
      *
      * @param rest is the suspended stream
      * @param cancel $cancelDesc
      */
    def wait[A](rest: F[Stream[F,A]], cancel: F[Unit]): Self[A] =
      fromStream(Stream.wait[F,A](rest, cancel))

    /** Returns an empty stream. */
    def empty[A]: Self[A] = fromStream(Stream.empty[F,A])

    /** Returns a stream that signals an error. */
    def raiseError[A](ex: Throwable): Self[A] =
      fromStream(Stream.raiseError[F,A](ex))

    /** Builds a stream equivalent with the [[Stream.Halt]] stream state.
      *
      * $haltDesc
      */
    def halt[A](ex: Option[Throwable]): Self[A] =
      fromStream(Stream.halt[F,A](ex))

    /** Converts any Scala `collection.immutable.LinearSeq`
      * into a stream.
      */
    def fromList[A](list: LinearSeq[A]): Self[A] =
      fromStream(Stream.fromList[F,A](list))

    /** Converts a `scala.collection.immutable.Seq` into a stream. */
    def fromSeq[A](seq: Seq[A]): Self[A] =
      fromStream(Stream.fromSeq[F,A](seq))

    /** $fromIterableDesc
      *
      * @param iterable is the reference to be converted to a stream
      */
    def fromIterable[A](iterable: Iterable[A]): Self[A] =
      fromStream(Stream.fromIterable[F,A](iterable))

    /** $fromIterableDesc
      *
      * @param iterable is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIterable[A](iterable: Iterable[A], batchSize: Int): Self[A] =
      fromStream(Stream.fromIterable[F,A](iterable, batchSize))

    /** $fromIteratorDesc
      *
      * @param iterator is the reference to be converted to a stream
      */
    def fromIterator[A](iterator: Iterator[A]): Self[A] =
      fromStream(Stream.fromIterator[F,A](iterator))

    /** $fromIteratorDesc
      *
      * @param iterator is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIterator[A](iterator: Iterator[A], batchSize: Int): Self[A] =
      fromStream(Stream.fromIterator[F,A](iterator, batchSize))
  }
}
