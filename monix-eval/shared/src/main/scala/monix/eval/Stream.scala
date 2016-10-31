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

import scala.collection.immutable.LinearSeq
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/** The `Stream` is a type that describes lazy, possibly asynchronous
  * streaming of elements.
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
  * is over, or a (head, tail) pair, very similar in spirit to
  * Scala's standard `List` or `Stream`.
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
  *  - [[monix.eval.Stream.Suspend Suspend]] is for suspending the
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
  * As restriction, this `F[_]` type used must be stack safe in
  * `map` and `flatMap`.
  *
  * ATTRIBUTION: this type was inspired by the `Streaming` type in the
  * Typelevel Cats library (later moved to Typelevel's Dogs), originally
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
  final def onStop(implicit F: Applicative[F]): F[Unit] =
    this match {
      case Cons(_, _, ref) => ref
      case ConsLazy(_, _, ref) => ref
      case ConsSeq(_, _, ref) => ref
      case Suspend(_, ref) => ref
      case Halt(_) => F.unit
    }

  /** Given a mapping function that returns a possibly lazy or asynchronous
    * result, applies it over the elements emitted by the stream.
    */
  final def mapEval[B](f: A => F[B])(implicit F: Monad[F]): Stream[F, B] = {
    import F.{applicative, functor}

    this match {
      case Cons(head, tail, stop) =>
        try ConsLazy[F,B](f(head), tail.map(_.mapEval(f)), stop)
        catch { case NonFatal(ex) => signalError(ex, stop) }

      case ConsLazy(headF, tail, stop) =>
        try ConsLazy[F,B](F.flatMap(headF)(f), tail.map(_.mapEval(f)), stop)
        catch { case NonFatal(ex) => signalError(ex, stop) }

      case ConsSeq(headSeq, tailF, stop) =>
        if (headSeq.isEmpty)
          Suspend[F,B](tailF.map(_.mapEval(f)), stop)
        else {
          val head = headSeq.head
          val tail = F.applicative.pure(ConsSeq(headSeq.tail, tailF, stop))

          try ConsLazy[F,B](f(head), tail.map(_.mapEval(f)), stop)
          catch { case NonFatal(ex) => signalError(ex, stop) }
        }

      case Suspend(rest, stop) =>
        Suspend[F,B](F.functor.map(rest)(_.mapEval(f)), stop)

      case halt @ Halt(_) =>
        halt
    }
  }

  /** Returns a new stream by mapping the supplied function
    * over the elements of the source.
    */
  final def map[B](f: A => B)(implicit F: Applicative[F]): Stream[F,B] = {
    import F.functor

    this match {
      case Cons(head, tail, stop) =>
        try
          Cons[F,B](f(head), tail.map(_.map(f)), stop)
        catch { case NonFatal(ex) =>
          signalError(ex, stop)
        }

      case ConsLazy(headF, tail, stop) =>
        try
          ConsLazy[F,B](headF.map(f), tail.map(_.map(f)), stop)
        catch { case NonFatal(ex) =>
          signalError(ex, stop)
        }

      case ConsSeq(head, rest, stop) =>
        try
          ConsSeq[F,B](head.map(f), rest.map(_.map(f)), stop)
        catch { case NonFatal(ex) =>
          signalError(ex, stop)
        }

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.map(f)), stop)

      case empty @ Halt(_) =>
        empty
    }
  }

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => Stream[F,B])(implicit F: Monad[F]): Stream[F,B] = {
    import F.{applicative, functor}

    this match {
      case Cons(head, tail, stop) =>
        try f(head) concatF tail.map(_.flatMap(f)) catch {
          case NonFatal(ex) => signalError(ex, stop)
        }

      case ConsSeq(list, rest, stop) =>
        try if (list.isEmpty)
          suspend[F,B](rest.map(_.flatMap(f)), stop)
        else
          f(list.head) concatF F.applicative.pure {
            ConsSeq[F,A](list.tail, rest, stop).flatMap(f)
          }
        catch {
          case NonFatal(ex) => signalError(ex, stop)
        }

      case ConsLazy(fa, rest, stop) =>
        Suspend[F,B](
          fa.map(head =>
            try f(head) concatF rest.map(_.flatMap(f)) catch {
              case NonFatal(ex) => signalError(ex, stop)
            }),
          stop
        )

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.flatMap(f)), stop)

      case empty @ Halt(_) =>
        empty
    }
  }

  /** Appends the given stream to the end of the source,
    * effectively concatenating them.
    */
  final def ++[B >: A](rhs: Stream[F,B])(implicit F: Monad[F]): Stream[F,B] =
    this.concatF(F.applicative.pure(rhs))

  /** Appends the given lazily generated stream to the end
    * of the source, effectively concatenating them.
    */
  final def ++[B >: A](rhs: F[Stream[F,B]])(implicit F: Monad[F]): Stream[F,B] =
    this.concatF(rhs)

  private final def concatF[B >: A](rhs: F[Stream[F,B]])(implicit F: Monad[F]): Stream[F,B] = {
    import F.{applicative, functor}

    this match {
      case Cons(a, lt, stop) =>
        val composite = stop.flatMap(_ => F.flatMap(rhs)(_.onStop))
        Cons[F,B](a, lt.map(_.concatF(rhs)), composite)

      case ConsLazy(fa, lt, stop) =>
        val composite = stop.flatMap(_ => F.flatMap(rhs)(_.onStop))
        ConsLazy[F,B](fa.asInstanceOf[F[B]], lt.map(_.concatF(rhs)), composite)

      case ConsSeq(seq, lt, stop) =>
        val composite = stop.flatMap(_ => F.flatMap(rhs)(_.onStop))
        ConsSeq[F,B](seq, lt.map(_.concatF(rhs)), composite)

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.concatF(rhs)), stop)

      case Halt(None) =>
        Suspend[F,B](rhs, rhs.flatMap(_.onStop))

      case error @ Halt(Some(_)) =>
        val stop = rhs.flatMap(_.onStop)
        Suspend[F,B](stop.map(_ => error.asInstanceOf[Stream[F,B]]), stop)
    }
  }

  /** Left associative fold using the function `f`.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  final def foldLeftL[S](seed: => S)(f: (S,A) => S)
    (implicit F: MonadEval[F], E: MonadError[F,Throwable]): F[S] = {

    def loop(self: Stream[F,A], state: S)(implicit A: Applicative[F], M: Monad[F]): F[S] = {
      def next(a: A, next: F[Stream[F,A]], stop: F[Unit]): F[S] =
        try {
          val newState = f(state, a)
          next.flatMap(loop(_, newState))
        } catch {
          case NonFatal(ex) =>
            stop.flatMap(_ => E.raiseError(ex))
        }

      self match {
        case Halt(None) =>
          A.pure(state)
        case Halt(Some(ex)) =>
          E.raiseError(ex)
        case Cons(a, tail, stop) =>
          next(a, tail, stop)
        case ConsLazy(headF, tail, stop) =>
          // Also protecting the head!
          val head = headF.onErrorHandleWith(ex => stop.flatMap(_ => E.raiseError(ex)))
          head.flatMap(a => next(a, tail, stop))
        case Suspend(rest, _) =>
          rest.flatMap(loop(_, state))
        case ConsSeq(list, next, stop) =>
          if (list.isEmpty)
            next.flatMap(loop(_, state))
          else try {
            val newState = list.foldLeft(state)(f)
            next.flatMap(loop(_, newState))
          } catch {
            case NonFatal(ex) =>
              stop.flatMap(_ => E.raiseError(ex))
          }
      }
    }

    implicit val A = F.applicative
    implicit val M = F.monad

    val init = F.eval(seed).onErrorHandleWith(ex => onStop.flatMap(_ => E.raiseError(ex)))
    init.flatMap(a => loop(self, a))
  }

  /** Aggregates all elements in a `List` and preserves order. */
  final def toListL[B >: A](implicit F: MonadEval[F], E: MonadError[F,Throwable]): F[List[B]] = {
    val folded = foldLeftL(mutable.ListBuffer.empty[B]) { (acc, a) => acc += a }
    F.functor.map(folded)(_.toList)
  }

  private def signalError(ex: Throwable, stop: F[Unit])
    (implicit F: Applicative[F]): Stream[F, Nothing] = {
    import F.functor
    val t = stop.map(_ => Stream.halt[F,Nothing](Some(ex)))
    Stream.Suspend[F,Nothing](t, stop)
  }
}

/** Defines [[Stream]] builders.
  *
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
  * @define suspendDesc The [[monix.eval.Stream.Suspend Suspend]] state
  *         of the [[Stream]] represents a suspended stream to be
  *         evaluated in the `F` context. It is useful to delay the
  *         evaluation of a stream by deferring to `F`.
  *
  * @define stopDesc is a computation to be executed in case
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
  *         Note that the generated `Iterator` is side-effectful and
  *         evaluating the `tail` references multiple times might yield
  *         undesired effects. So if you end up evaluating those `tail`
  *         references multiple times, consider using `memoize` on
  *         the resulting stream or apply `fromList` on an
  *         immutable `LinearSeq`.
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
  * @define batchSizeDesc indicates the size of a streamed batch on each event
  *        (by means of [[Stream.ConsSeq]]) or no batching done if it is
  *        equal to 1
  *
  * @define suspendEvalDesc Promote a non-strict value representing a
  *         stream to a stream of the same type, effectively delaying its
  *         initialisation.
  *
  *         In case the underlying evaluation monad `F` is a
  *         [[monix.types.Suspendable Suspendable]] type
  *         (like [[Task]] or [[Coeval]]), then suspension will act as a factory
  *         of streams, with any described side-effects happening on
  *         each evaluation.
  */
object Stream extends StreamInstances {
  /** Given a sequence of elements, builds a stream out of it. */
  def apply[F[_] : Applicative, A](elems: A*): Stream[F,A] =
    fromList[F,A](elems.toList)

  /** Lifts a strict value into the stream context,
    * returning a stream of one element.
    */
  def now[F[_],A](a: A)(implicit F: Applicative[F]): Stream[F,A] =
    cons[F,A](a, F.pure(empty[F,A]))

  /** Alias for [[now]]. */
  def pure[F[_],A](a: A)(implicit F: Applicative[F]): Stream[F,A] =
    now[F,A](a)(F)

  /** Lifts a non-strict value into the stream context,
    * returning a stream of one element that is lazily
    * evaluated.
    */
  def eval[F[_],A](a: => A)(implicit F: MonadEval[F]): Stream[F,A] =
    consLazy[F,A](F.eval(a), F.applicative.pure(empty[F,A]))(F.applicative)

  /** Builds a [[Stream.Cons]] stream state.
    *
    * $consDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def cons[F[_],A](head: A, tail: F[Stream[F,A]], stop: F[Unit]): Stream[F,A] =
    Cons[F,A](head, tail, stop)

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
    * @param stop $stopDesc
    */
  def consLazy[F[_],A](head: F[A], tail: F[Stream[F,A]], stop: F[Unit]): Stream[F,A] =
    ConsLazy[F,A](head, tail, stop)

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
    * @param stop $stopDesc
    */
  def consSeq[F[_],A](head: LinearSeq[A], tail: F[Stream[F,A]], stop: F[Unit]): Stream[F,A] =
    ConsSeq[F,A](head, tail, stop)

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

  /** $suspendEvalDesc */
  def suspend[F[_], A](fa: => Stream[F,A])(implicit F: Suspendable[F]): Stream[F,A] =
    Stream.suspend[F,A](F.monadEval.eval(fa))(F.applicative)

  /** Alias for [[Stream.suspend[F[_],A](fa* suspend]]. */
  def defer[F[_] : Suspendable, A](fa: => Stream[F,A]): Stream[F,A] =
    suspend(fa)

  /** Builds a [[Stream.Suspend]] stream state.
    *
    * $suspendDesc
    *
    * @param rest is the suspended stream
    */
  def suspend[F[_],A](rest: F[Stream[F,A]])
    (implicit F: Applicative[F]): Stream[F,A] =
    suspend[F,A](rest, F.unit)

  /** Builds a [[Stream.Suspend]] stream state.
    *
    * $suspendDesc
    *
    * @param rest is the suspended stream
    * @param stop $stopDesc
    */
  def suspend[F[_],A](rest: F[Stream[F,A]], stop: F[Unit]): Stream[F,A] =
    Suspend[F,A](rest, stop)

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
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Stream[F,A] =
    ConsSeq[F,A](xs, F.pure(halt[F,A](None)), F.unit)

  /** Converts any Scala `collection.IndexedSeq` into a stream.
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIndexedSeq[F[_] : MonadEval, A](xs: IndexedSeq[A]): Stream[F,A] =
    fromIndexedSeq(xs, Platform.recommendedBatchSize)

  /** Converts any Scala `collection.IndexedSeq` into a stream.
    *
    * @param xs is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A], batchSize: Int)
    (implicit F: MonadEval[F]): Stream[F,A] = {

    // Recursive function
    def loop(idx: Int, length: Int, stop: F[Unit]): F[Stream[F,A]] =
      F.eval {
        if (idx >= length)
          Halt(None)
        else if (batchSize == 1)
          try Cons[F,A](xs(idx), loop(idx+1,length,stop), stop) catch {
            case NonFatal(ex) => Halt(Some(ex))
          }
        else try {
          val buffer = ListBuffer.empty[A]
          var j = 0
          while (j + idx < length && j < batchSize) {
            buffer += xs(idx + j)
            j += 1
          }

          ConsSeq[F,A](buffer.toList, loop(idx + j, length, stop), stop)
        } catch {
          case NonFatal(ex) =>
            Halt(Some(ex))
        }
      }

    require(batchSize >= 1, "batchSize >= 1")
    val stop = F.applicative.unit
    Suspend[F,A](loop(0, xs.length, stop), stop)
  }

  /** Converts any `scala.collection.Seq` into a stream. */
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: MonadEval[F]): Stream[F,A] =
    xs match {
      case ref: LinearSeq[_] =>
        fromList[F,A](ref.asInstanceOf[LinearSeq[A]])(F.applicative)
      case ref: IndexedSeq[_] =>
        fromIndexedSeq[F,A](ref.asInstanceOf[IndexedSeq[A]], Platform.recommendedBatchSize)
      case _ =>
        fromIterable(xs, batchSize = Platform.recommendedBatchSize)
    }

  /** $fromIterableDesc
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIterable[F[_] : MonadEval, A](xs: Iterable[A]): Stream[F,A] =
    fromIterable[F,A](xs, 1)

  /** $fromIterableDesc
    *
    * @param xs is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIterable[F[_],A](xs: Iterable[A], batchSize: Int)
    (implicit F: MonadEval[F]): Stream[F,A] = {

    require(batchSize > 0, "batchSize should be strictly positive")
    val init = F.eval(xs.iterator)
    val stop = F.applicative.unit
    val rest = F.functor.map(init)(iterator => fromIterator[F,A](iterator, batchSize))
    Suspend[F,A](rest, stop)
  }

  /** $fromIteratorDesc
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIterator[F[_] : MonadEval, A](xs: Iterator[A]): Stream[F,A] =
    fromIterator[F,A](xs, batchSize = 1)

  /** $fromIteratorDesc
    *
    * @param xs is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIterator[F[_], A](xs: Iterator[A], batchSize: Int)
    (implicit F: MonadEval[F]): Stream[F,A] = {

    def loop(): F[Stream[F,A]] =
      F.eval {
        try {
          val buffer = mutable.ListBuffer.empty[A]
          var processed = 0
          while (processed < batchSize && xs.hasNext) {
            buffer += xs.next()
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
    Suspend[F,A](loop(), F.applicative.unit)
  }

  /** $consDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  final case class Cons[F[_],A](
    head: A,
    tail: F[Stream[F,A]],
    stop: F[Unit])
    extends Stream[F,A]

  /** $consLazyDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  final case class ConsLazy[F[_],A](
    head: F[A],
    tail: F[Stream[F,A]],
    stop: F[Unit])
    extends Stream[F,A]

  /** $consSeqDesc
    *
    * @param head is a strict list of the next elements to be processed, can be empty
    * @param tail is the rest of the stream
    * @param stop $stopDesc
    */
  final case class ConsSeq[F[_],A](
    head: LinearSeq[A],
    tail: F[Stream[F,A]],
    stop: F[Unit])
    extends Stream[F,A]

  /** $suspendDesc
    *
    * @param rest is the suspended stream
    * @param stop $stopDesc
    */
  final case class Suspend[F[_], A](
    rest: F[Stream[F,A]],
    stop: F[Unit])
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

    import M.{applicative, monadEval, monad}

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

    /** Returns a new stream by mapping the supplied function
      * over the elements of the source.
      */
    final def map[B](f: A => B): Self[B] =
      transform(_.map(f)(M.applicative))

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

  /** A template for companion objects of [[Stream.Like]] subtypes.
    *
    * This type is not meant for providing polymorphic behavior, but
    * for sharing implementation between types such as
    * [[TaskStream]] and [[CoevalStream]].
    *
    * @tparam Self is the type of the inheriting subtype
    * @tparam F is the monadic type that handles evaluation in the
    *         [[Stream]] implementation (e.g. [[Task]], [[Coeval]])
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
    * @define consDesc Builds a stream equivalent with [[Stream.Cons]],
    *         a pairing between a `head` and a potentially lazy or
    *         asynchronous `tail`.
    *
    *         @see [[Stream.Cons]]
    *
    * @define consSeqDesc Builds a stream equivalent with [[Stream.ConsSeq]],
    *         a pairing between a `head`, which is a strict sequence and a
    *         potentially lazy or asynchronous `tail`.
    *
    *         @see [[Stream.ConsSeq]]
    *
    * @define consLazyDesc Builds a stream equivalent with [[Stream.ConsLazy]],
    *         a pairing between a lazy, potentially asynchronous `head` and a
    *         potentially lazy or asynchronous `tail`.
    *
    *         @see [[Stream.ConsLazy]]
    *
    * @define suspendDesc Builds a stream equivalent with [[Stream.Suspend]],
    *         representing a suspended stream, useful for delaying its
    *         initialization, the evaluation being controlled by the
    *         underlying monadic context (e.g. [[Task]], [[Coeval]], etc).
    *
    *         @see [[Stream.Suspend]]
    *
    * @define haltDesc Builds an empty stream that can potentially signal an
    *         error.
    *
    *         Used as a final node of a stream (the equivalent of Scala's `Nil`),
    *         wrapping a [[Stream.Halt]] instance.
    *
    *         @see [[Stream.Halt]]
    *
    * @define stopDesc is a computation to be executed in case
    *         streaming is stopped prematurely, giving it a chance
    *         to do resource cleanup (e.g. close file handles)
    *
    * @define batchSizeDesc indicates the size of a streamed batch
    *         on each event (generating [[Stream.ConsSeq]] nodes) or no
    *         batching done if it is equal to 1
    */
  abstract class Builders[F[_], Self[+T] <: Like[T, F, Self]]
    (implicit E: MonadError[F,Throwable], M: Memoizable[F]) { self =>

    import M.{applicative, functor, monadEval, suspendable}

    /** Materializes a [[Stream]]. */
    def fromStream[A](stream: Stream[F,A]): Self[A]

    /** Given a sequence of elements, builds a stream out of it. */
    def apply[A](elems: A*): Self[A] =
      fromStream(Stream.apply[F,A](elems:_*))

    /** Lifts a strict value into the stream context,
      * returning a stream of one element.
      */
    def now[A](a: A): Self[A] =
      fromStream(Stream.now[F,A](a))

    /** Alias for [[now]]. */
    def pure[A](a: A): Self[A] =
      fromStream(Stream.pure[F,A](a))

    /** Lifts a non-strict value into the stream context,
      * returning a stream of one element that is lazily
      * evaluated.
      */
    def eval[A](a: => A): Self[A] =
      fromStream(Stream.eval[F,A](a))

    /** $consDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def cons[A](head: A, tail: F[Self[A]]): Self[A] =
      fromStream(Stream.cons[F,A](head, functor.map(tail)(_.stream)))

    /** $consDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param stop $stopDesc
      */
    def cons[A](head: A, tail: F[Self[A]], stop: F[Unit]): Self[A] =
      fromStream(Stream.cons[F,A](head, functor.map(tail)(_.stream), stop))

    /** $consLazyDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def consLazy[A](head: F[A], tail: F[Self[A]]): Self[A] =
      fromStream(Stream.consLazy[F,A](head, functor.map(tail)(_.stream)))

    /** $consLazyDesc
      *
      * @param head is the current element to be signaled
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param stop $stopDesc
      */
    def consLazy[A](head: F[A], tail: F[Self[A]], stop: F[Unit]): Self[A] =
      fromStream(Stream.consLazy[F,A](head, functor.map(tail)(_.stream), stop))

    /** $consSeqDesc
      *
      * @param head is a strict list of the next elements to be processed, can be empty
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def consSeq[A](head: LinearSeq[A], tail: F[Self[A]]): Self[A] =
      fromStream(Stream.consSeq[F,A](head, functor.map(tail)(_.stream)))

    /** $consSeqDesc
      *
      * @param head is a strict list of the next elements to be processed, can be empty
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param stop $stopDesc
      */
    def consSeq[A](head: LinearSeq[A], tail: F[Self[A]], stop: F[Unit]): Self[A] =
      fromStream(Stream.consSeq[F,A](head, functor.map(tail)(_.stream), stop))

    /** Promote a non-strict value representing a stream to a stream
      * of the same type, effectively delaying its initialisation.
      *
      * The suspension will act as a factory of streams, with any
      * described side-effects happening on each evaluation.
      */
    def suspend[A](fa: => Self[A]): Self[A] =
      fromStream(Stream.suspend[F,A](fa.stream))

    /** Alias for [[Builders.suspend[A](fa* suspend]]. */
    def defer[A](fa: => Self[A]): Self[A] =
      fromStream(Stream.defer[F,A](fa.stream))

    /** $suspendDesc
      *
      * @param rest is the suspended stream
      */
    def suspend[A](rest: F[Self[A]]): Self[A] =
      fromStream(Stream.suspend[F,A](rest.map(_.stream)))

    /** $suspendDesc
      *
      * @param rest is the suspended stream
      * @param stop $stopDesc
      */
    def suspend[A](rest: F[Self[A]], stop: F[Unit]): Self[A] =
      fromStream(Stream.suspend[F,A](rest.map(_.stream), stop))

    /** Returns an empty stream. */
    def empty[A]: Self[A] = fromStream(Stream.empty[F,A])

    /** Returns a stream that signals an error. */
    def raiseError[A](ex: Throwable): Self[A] =
      fromStream(Stream.raiseError[F,A](ex))

    /** $haltDesc */
    def halt[A](ex: Option[Throwable]): Self[A] =
      fromStream(Stream.halt[F,A](ex))

    /** Converts any Scala `collection.IndexedSeq` into a stream.
      *
      * @param xs is the reference to be converted to a stream
      */
    def fromIndexedSeq[A](xs: IndexedSeq[A]): Self[A] =
      fromStream(Stream.fromIndexedSeq[F,A](xs))

    /** Converts any Scala `collection.IndexedSeq` into a stream.
      *
      * @param xs is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIndexedSeq[A](xs: IndexedSeq[A], batchSize: Int): Self[A] =
      fromStream(Stream.fromIndexedSeq[F,A](xs, batchSize))

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
      * @param xs is the reference to be converted to a stream
      */
    def fromIterable[A](xs: Iterable[A]): Self[A] =
      fromStream(Stream.fromIterable[F,A](xs))

    /** $fromIterableDesc
      *
      * @param xs is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIterable[A](xs: Iterable[A], batchSize: Int): Self[A] =
      fromStream(Stream.fromIterable[F,A](xs, batchSize))

    /** $fromIteratorDesc
      *
      * @param xs is the reference to be converted to a stream
      */
    def fromIterator[A](xs: Iterator[A]): Self[A] =
      fromStream(Stream.fromIterator[F,A](xs))

    /** $fromIteratorDesc
      *
      * @param xs is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIterator[A](xs: Iterator[A], batchSize: Int): Self[A] =
      fromStream(Stream.fromIterator[F,A](xs, batchSize))

    /** Type-class instances for [[Stream.Like]] types. */
    implicit val typeClassInstances: TypeClassInstances =
      new TypeClassInstances

    /** Type-class instances for [[Stream.Like]] types. */
    class TypeClassInstances extends Suspendable.Instance[Self] {
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
}

private[eval] trait StreamInstances extends StreamInstances1 {
  /** Provides a [[monix.types.Suspendable Suspendable]] instance
    * for [[Stream]].
    */
  implicit def suspendableInstance[F[_] : Suspendable]: SuspendableInstance[F] =
    new SuspendableInstance[F]()

  /** Provides a [[monix.types.Suspendable Suspendable]] instance
    * for [[Stream]].
    */
  class SuspendableInstance[F[_]](implicit F: Suspendable[F])
    extends MonadEvalInstance[F]()(F.monadEval)
      with Suspendable.Instance[({type λ[+α] = Stream[F,α]})#λ] {

    override def suspend[A](fa: => Stream[F, A]): Stream[F, A] =
      Stream.suspend(fa)
  }
}

private[eval] trait StreamInstances1 extends StreamInstances0 {
  /** Provides a [[monix.types.MonadEval MonadEval]] instance
    * for [[Stream]].
    */
  implicit def monadEvalInstance[F[_] : MonadEval]: MonadEvalInstance[F] =
    new MonadEvalInstance[F]()

  /** Provides a [[monix.types.MonadEval MonadEval]] instance
    * for [[Stream]].
    */
  class MonadEvalInstance[F[_]](implicit F: MonadEval[F])
    extends MonadInstance[F]()(F.monad)
    with MonadEval.Instance[({type λ[+α] = Stream[F,α]})#λ] {

    def eval[A](a: => A): Stream[F, A] =
      Stream.eval[F,A](a)
  }
}

private[eval] trait StreamInstances0 {
  /** Provides a [[monix.types.Monad]] instance for [[Stream]]. */
  implicit def monadInstance[F[_] : Monad]: MonadInstance[F] =
    new MonadInstance[F]()

  /** Provides a [[monix.types.Monad]] instance for [[Stream]]. */
  class MonadInstance[F[_]](implicit F: Monad[F])
    extends Monad.Instance[({type λ[+α] = Stream[F,α]})#λ] {

    def pure[A](a: A): Stream[F, A] =
      Stream.pure[F,A](a)(F.applicative)
    def flatMap[A, B](fa: Stream[F, A])(f: (A) => Stream[F, B]): Stream[F, B] =
      fa.flatMap(f)
    def map[A, B](fa: Stream[F, A])(f: (A) => B): Stream[F, B] =
      fa.map(f)(F.applicative)
    def map2[A, B, Z](fa: Stream[F, A], fb: Stream[F, B])(f: (A, B) => Z): Stream[F, Z] =
      fa.flatMap(a => fb.map(b => f(a,b))(F.applicative))
    def ap[A, B](ff: Stream[F, (A) => B])(fa: Stream[F, A]): Stream[F, B] =
      ff.flatMap(f => fa.map(a => f(a))(F.applicative))
  }
}