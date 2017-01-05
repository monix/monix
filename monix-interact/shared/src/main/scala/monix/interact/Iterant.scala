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

import monix.types._
import monix.types.syntax._
import monix.execution.internal.Platform
import scala.collection.immutable.LinearSeq
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/** The `Iterant` is a type that describes lazy, possibly asynchronous
  * streaming of elements.
  *
  * It is similar somewhat in spirit to Scala's own
  * `collection.immutable.Stream` and with Java's `Iterable`, except
  * that it is more composable and more flexible due to evaluation being
  * controlled by an `F[_]` monadic type that you have to supply
  * (like [[monix.eval.Task Task]] or [[monix.eval.Coeval Coeval]])
  * which will control the evaluation. In other words, this `Iterant`
  * type is capable of strict or lazy, synchronous or asynchronous
  * evaluation.
  *
  * Consumption of a `Iterant` happens typically in a loop where
  * the current step represents either a signal that the stream
  * is over, or a (head, tail) pair, very similar in spirit to
  * Scala's standard `List` or `Iterant`.
  *
  * The type is an ADT, meaning a composite of the following types:
  *
  *  - [[monix.interact.Iterant.Next Next]] which signals a single strict
  *    element, the `head` and a `tail` representing the rest of the stream
  *  - [[monix.interact.Iterant.NextSeq NextSeq]] is a variation on `Next`
  *    for signaling a whole strict batch of elements as the `head`,
  *    along with the `tail` representing the rest of the stream
  *  - [[monix.interact.Iterant.Suspend Suspend]] is for suspending the
  *    evaluation of a stream
  *  - [[monix.interact.Iterant.Halt Halt]] represents an empty
  *    stream, signaling the end, either in success or in error
  *
  * The `Iterant` type accepts as type parameter an `F` monadic type
  * that is used to control how evaluation happens. For example you can
  * use [[monix.eval.Task Task]], in which case the streaming can have
  * asynchronous behavior, or you can use [[monix.eval.Coeval Coeval]]
  * in which case it can behave like a normal, synchronous `Iterable`.
  *
  * As restriction, this `F[_]` type used must be stack safe in
  * `map` and `flatMap`.
  *
  * ATTRIBUTION: this type was inspired by the `Streaming` type in the
  * Typelevel Cats library (later moved to Typelevel's Dogs), originally
  * committed in Cats by Erik Osheim. Several operations from `Streaming`
  * were adapted for this `Iterant` type, like `flatMap`, `foldRightL`
  * and `zipMap`.
  *
  * @tparam F is the monadic type that controls evaluation; note that it
  *         must be stack-safe in its `map` and `flatMap` operations
  *
  * @tparam A is the type of the elements produced by this Iterant
  */
sealed abstract class Iterant[F[_], +A] extends Product with Serializable { self =>
  import Iterant._

  /** Returns a computation that should be evaluated in
    * case the streaming must be canceled before reaching
    * the end.
    *
    * This is useful to release any acquired resources,
    * like opened file handles or network sockets.
    */
  final def onStop(implicit F: Applicative[F]): F[Unit] =
    this match {
      case Next(_, _, ref) => ref
      case NextSeq(_, _, ref) => ref
      case Suspend(_, ref) => ref
      case Halt(_) => F.unit
    }

  /** Given a routine make sure to execute it whenever
    * the consumer executes the current `stop` action.
    */
  final def doOnStop(f: F[Unit])(implicit F: Monad[F]): Iterant[F,A] = {
    import F.functor
    this match {
      case Next(head, tail, stop) =>
        Next(head, tail.map(_.doOnStop(f)), stop.flatMap(_ => f))
      case NextSeq(head, tail, stop) =>
        NextSeq(head, tail.map(_.doOnStop(f)), stop.flatMap(_ => f))
      case Suspend(tail, stop) =>
        Suspend(tail.map(_.doOnStop(f)), stop.flatMap(_ => f))
      case halt @ Halt(_) =>
        halt // nothing to do
    }
  }

  /** Given a mapping function that returns a possibly lazy or asynchronous
    * result, applies it over the elements emitted by the stream.
    */
  final def mapEval[B](f: A => F[B])(implicit F: Applicative[F]): Iterant[F, B] = {
    import F.functor
    this match {
      case Next(head, tail, stop) =>
        try {
          val fa = f(head)
          val rest = fa.map(h => nextS(h, tail.map(_.mapEval(f)), stop))
          Suspend(rest, stop)
        } catch {
          case NonFatal(ex) => signalError(ex, stop)
        }

      case NextSeq(headSeq, tailF, stop) =>
        if (headSeq.isEmpty)
          Suspend[F,B](tailF.map(_.mapEval(f)), stop)
        else try {
          val head = headSeq.head
          val tail = F.pure(NextSeq(headSeq.tail, tailF, stop))
          val fa = f(head)
          val rest = fa.map(h => nextS(h, tail.map(_.mapEval(f)), stop))
          Suspend[F,B](rest, stop)
        }
        catch { case NonFatal(ex) =>
          signalError(ex, stop)
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
  final def map[B](f: A => B)(implicit F: Applicative[F]): Iterant[F,B] = {
    import F.functor
    this match {
      case Next(head, tail, stop) =>
        try Next[F,B](f(head), tail.map(_.map(f)), stop)
        catch { case NonFatal(ex) => signalError(ex, stop) }

      case NextSeq(head, rest, stop) =>
        try NextSeq[F,B](head.map(f), rest.map(_.map(f)), stop)
        catch { case NonFatal(ex) => signalError(ex, stop) }

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.map(f)), stop)

      case empty @ Halt(_) =>
        empty
    }
  }

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => Iterant[F,B])(implicit F: Monad[F]): Iterant[F,B] = {
    import F.functor
    import F.{applicative => A}
    this match {
      case Next(head, tail, stop) =>
        try f(head).doOnStop(stop) ++ tail.map(_.flatMap(f))
        catch { case NonFatal(ex) => signalError(ex, stop) }

      case NextSeq(list, rest, stop) =>
        try if (list.isEmpty) {
          Suspend[F,B](rest.map(_.flatMap(f)), stop)
        }
        else {
          f(list.head).doOnStop(stop) ++
            A.pure(NextSeq[F,A](list.tail, rest, stop)).map(_.flatMap(f))
        }
        catch {
          case NonFatal(ex) => signalError(ex, stop)
        }

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.flatMap(f)), stop)
      case empty @ Halt(_) =>
        empty
    }
  }

  /** Appends the given stream to the end of the source,
    * effectively concatenating them.
    */
  final def ++[B >: A](rhs: Iterant[F,B])(implicit F: Functor[F]): Iterant[F,B] =
    this.concat(rhs)

  /** Appends the given lazily generated stream to the end
    * of the source, effectively concatenating them.
    */
  final def ++[B >: A](rhs: F[Iterant[F,B]])(implicit F: Applicative[F]): Iterant[F,B] =
    this.concat(Suspend(rhs, F.unit))(F.functor)

  private final def concat[B >: A](rhs: Iterant[F,B])(implicit F: Functor[F]): Iterant[F,B] =
    this match {
      case Next(a, lt, stop) =>
        val rest = lt.map(_.concat(rhs))
        Next[F,B](a, rest, stop)
      case NextSeq(seq, lt, stop) =>
        val rest = lt.map(_.concat(rhs))
        NextSeq[F,B](seq, rest, stop)
      case Suspend(lt, stop) =>
        val rest = lt.map(_.concat(rhs))
        Suspend[F,B](rest, stop)
      case Halt(None) =>
        rhs
      case error @ Halt(Some(_)) =>
        error
    }

  /** Left associative fold using the function `f`.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  final def foldLeftL[S](seed: => S)(f: (S,A) => S)
    (implicit F: MonadEval[F], E: MonadError[F,Throwable]): F[S] = {

    def loop(self: Iterant[F,A], state: S)(implicit A: Applicative[F], M: Monad[F]): F[S] = {
      def next(a: A, next: F[Iterant[F,A]], stop: F[Unit]): F[S] =
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
        case Next(a, tail, stop) =>
          next(a, tail, stop)
        case Suspend(rest, _) =>
          rest.flatMap(loop(_, state))
        case NextSeq(list, next, stop) =>
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
    (implicit F: Applicative[F]): Iterant[F, Nothing] = {
    import F.functor
    val t = stop.map(_ => Iterant.halt[F,Nothing](Some(ex)))
    Iterant.Suspend[F,Nothing](t, stop)
  }
}

/** Defines [[Iterant]] builders.
  *
  * @define nextDesc The [[monix.interact.Iterant.Next Next]] state
  *         of the [[Iterant]] represents a `head` / `tail`
  *         cons pair, where the `head` is a strict value.
  *
  *         Note the `head` being a strict value means that it is
  *         already known, whereas the `tail` is meant to be lazy and
  *         can have asynchronous behavior as well, depending on the `F`
  *         type used.
  *
  *         See [[monix.interact.Iterant.NextSeq NextSeq]]
  *         for a state where the head is a strict immutable list.
  *
  * @define nextSeqDesc The [[monix.interact.Iterant.NextSeq NextSeq]] state
  *         of the [[Iterant]] represents a `head` / `tail` cons pair,
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
  * @define suspendDesc The [[monix.interact.Iterant.Suspend Suspend]] state
  *         of the [[Iterant]] represents a suspended stream to be
  *         evaluated in the `F` context. It is useful to delay the
  *         evaluation of a stream by deferring to `F`.
  *
  * @define stopDesc is a computation to be executed in case
  *         streaming is stopped prematurely, giving it a chance
  *         to do resource cleanup (e.g. close file handles)
  *
  * @define haltDesc The [[monix.interact.Iterant.Halt Halt]] state
  *         of the [[Iterant]] represents the completion state
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
  *        (by means of [[Iterant.NextSeq]]) or no batching done if it is
  *        equal to 1
  *
  * @define suspendEvalDesc Promote a non-strict value representing a
  *         stream to a stream of the same type, effectively delaying its
  *         initialisation.
  *
  *         In case the underlying evaluation monad `F` is a
  *         [[monix.types.Suspendable Suspendable]] type
  *         (like [[monix.eval.Task Task]] or [[monix.eval.Coeval Coeval]]), then suspension will act as a factory
  *         of streams, with any described side-effects happening on
  *         each evaluation.
  */
object Iterant extends StreamInstances {
  /** Given a sequence of elements, builds a stream out of it. */
  def apply[F[_] : Applicative, A](elems: A*): Iterant[F,A] =
    fromList[F,A](elems.toList)

  /** Lifts a strict value into the stream context,
    * returning a stream of one element.
    */
  def now[F[_],A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
    next[F,A](a, F.pure(empty[F,A]))

  /** Alias for [[now]]. */
  def pure[F[_],A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
    now[F,A](a)(F)

  /** Lifts a non-strict value into the stream context,
    * returning a stream of one element that is lazily
    * evaluated.
    */
  def eval[F[_],A](a: => A)(implicit F: MonadEval[F]): Iterant[F,A] = {
    import F.{functor, applicative => A}
    Suspend(F.eval(a).map(r => nextS[F,A](r, A.pure(Halt(None)), A.unit)), A.unit)
  }

  /** Builds a [[Iterant.Next]] stream state.
    *
    * $nextDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def nextS[F[_],A](head: A, tail: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    Next[F,A](head, tail, stop)

  /** Builds a [[Iterant.Next]] stream state.
    *
    * $nextDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    */
  def next[F[_],A](head: A, tail: F[Iterant[F,A]])
    (implicit F: Applicative[F]): Iterant[F,A] =
    nextS[F,A](head, tail, F.unit)

  /** Builds a [[Iterant.NextSeq]] stream state.
    *
    * $nextSeqDesc
    *
    * @param head is the current element to be signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def nextSeqS[F[_],A](head: LinearSeq[A], tail: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    NextSeq[F,A](head, tail, stop)

  /** Builds a [[Iterant.NextSeq]] stream state.
    *
    * $nextSeqDesc
    *
    * @param head is a strict list of the next elements to be processed, can be empty
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    */
  def nextSeq[F[_],A](head: LinearSeq[A], tail: F[Iterant[F,A]])
    (implicit F: Applicative[F]): Iterant[F,A] =
    nextSeqS[F,A](head, tail, F.unit)

  /** $suspendEvalDesc */
  def suspend[F[_], A](fa: => Iterant[F,A])(implicit F: Suspendable[F]): Iterant[F,A] =
    Iterant.suspend[F,A](F.monadEval.eval(fa))(F.applicative)

  /** Alias for [[Iterant.suspendS[F[_],A](fa* suspend]]. */
  def defer[F[_] : Suspendable, A](fa: => Iterant[F,A]): Iterant[F,A] =
    suspend(fa)

  /** Builds a [[Iterant.Suspend]] stream state.
    *
    * $suspendDesc
    *
    * @param rest is the suspended stream
    */
  def suspend[F[_],A](rest: F[Iterant[F,A]])
    (implicit F: Applicative[F]): Iterant[F,A] =
    suspendS[F,A](rest, F.unit)

  /** Builds a [[Iterant.Suspend]] stream state.
    *
    * $suspendDesc
    *
    * @param rest is the suspended stream
    * @param stop $stopDesc
    */
  def suspendS[F[_],A](rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    Suspend[F,A](rest, stop)

  /** Returns an empty stream. */
  def empty[F[_],A]: Iterant[F,A] =
    Halt[F](None)

  /** Returns a stream that signals an error. */
  def raiseError[F[_],A](ex: Throwable): Iterant[F,A] =
    Halt[F](Some(ex))

  /** Builds a [[Iterant.Halt]] stream state.
    *
    * $haltDesc
    */
  def halt[F[_],A](ex: Option[Throwable]): Iterant[F,A] =
    Halt[F](ex)

  /** Converts any Scala `collection.immutable.LinearSeq`
    * into a stream.
    */
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F,A] =
    NextSeq[F,A](xs, F.pure(halt[F,A](None)), F.unit)

  /** Converts any Scala `collection.IndexedSeq` into a stream.
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIndexedSeq[F[_] : MonadEval, A](xs: IndexedSeq[A]): Iterant[F,A] =
    fromIndexedSeq(xs, Platform.recommendedBatchSize)

  /** Converts any Scala `collection.IndexedSeq` into a stream.
    *
    * @param xs is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A], batchSize: Int)
    (implicit F: MonadEval[F]): Iterant[F,A] = {

    // Recursive function
    def loop(idx: Int, length: Int, stop: F[Unit]): F[Iterant[F,A]] =
      F.eval {
        if (idx >= length)
          Halt(None)
        else if (batchSize == 1)
          try Next[F,A](xs(idx), loop(idx+1,length,stop), stop) catch {
            case NonFatal(ex) => Halt(Some(ex))
          }
        else try {
          val buffer = ListBuffer.empty[A]
          var j = 0
          while (j + idx < length && j < batchSize) {
            buffer += xs(idx + j)
            j += 1
          }

          NextSeq[F,A](buffer.toList, loop(idx + j, length, stop), stop)
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
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: MonadEval[F]): Iterant[F,A] =
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
  def fromIterable[F[_] : MonadEval, A](xs: Iterable[A]): Iterant[F,A] =
    fromIterable[F,A](xs, 1)

  /** $fromIterableDesc
    *
    * @param xs is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIterable[F[_],A](xs: Iterable[A], batchSize: Int)
    (implicit F: MonadEval[F]): Iterant[F,A] = {

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
  def fromIterator[F[_] : MonadEval, A](xs: Iterator[A]): Iterant[F,A] =
    fromIterator[F,A](xs, batchSize = 1)

  /** $fromIteratorDesc
    *
    * @param xs is the reference to be converted to a stream
    * @param batchSize $batchSizeDesc
    */
  def fromIterator[F[_], A](xs: Iterator[A], batchSize: Int)
    (implicit F: MonadEval[F]): Iterant[F,A] = {

    def loop(): F[Iterant[F,A]] =
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
            Next[F,A](buffer.head, loop(), F.applicative.unit)
          else
            NextSeq[F,A](buffer.toList, loop(), F.applicative.unit)
        } catch {
          case NonFatal(ex) =>
            Halt(Some(ex))
        }
      }

    require(batchSize > 0, "batchSize should be strictly positive")
    Suspend[F,A](loop(), F.applicative.unit)
  }

  /** $nextDesc
    *
    * @param head is the current element being signaled
    * @param tail is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  final case class Next[F[_],A](
    head: A,
    tail: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $nextSeqDesc
    *
    * @param head is a strict list of the next elements to be processed, can be empty
    * @param tail is the rest of the stream
    * @param stop $stopDesc
    */
  final case class NextSeq[F[_],A](
    head: LinearSeq[A],
    tail: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $suspendDesc
    *
    * @param rest is the suspended stream
    * @param stop $stopDesc
    */
  final case class Suspend[F[_], A](
    rest: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $haltDesc
    *
    * @param ex is an optional exception that, in case it is
    *        present, it means that the streaming ended in error.
    */
  final case class Halt[F[_]](ex: Option[Throwable])
    extends Iterant[F,Nothing]

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
  abstract class Like[+A, F[_], Self[+T] <: Like[T, F, Self]]
    (implicit E: MonadError[F,Throwable], M: Memoizable[F]) {
    self: Self[A] =>

    import M.{applicative, functor, monadEval}

    /** Returns the underlying [[Iterant]] that handles this stream. */
    def stream: Iterant[F,A]

    /** Given a mapping function from one [[Iterant]] to another,
      * applies it and returns a new stream based on the result.
      *
      * Must be implemented by inheriting subtypes.
      */
    protected def transform[B](f: Iterant[F,A] => Iterant[F,B]): Self[B]

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

  /** A template for companion objects of [[Iterant.Like]] subtypes.
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
    * @define nextSeqDesc Builds a stream equivalent with [[Iterant.NextSeq]],
    *         a pairing between a `head`, which is a strict sequence and a
    *         potentially lazy or asynchronous `tail`.
    *
    *         @see [[Iterant.NextSeq]]
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
  abstract class Builders[F[_], Self[+T] <: Like[T, F, Self]]
    (implicit E: MonadError[F,Throwable], M: Memoizable[F]) { self =>

    import M.{applicative, functor, monadEval, suspendable}

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
      */
    def next[A](head: A, tail: F[Self[A]]): Self[A] =
      fromStream(Iterant.next[F,A](head, functor.map(tail)(_.stream)))

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
      * @param head is a strict list of the next elements to be processed, can be empty
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      */
    def nextSeq[A](head: LinearSeq[A], tail: F[Self[A]]): Self[A] =
      fromStream(Iterant.nextSeq[F,A](head, functor.map(tail)(_.stream)))

    /** $nextSeqDesc
      *
      * @param head is a strict list of the next elements to be processed, can be empty
      * @param tail is the next state in the sequence that will
      *        produce the rest of the stream
      * @param stop $stopDesc
      */
    def nextSeqS[A](head: LinearSeq[A], tail: F[Self[A]], stop: F[Unit]): Self[A] =
      fromStream(Iterant.nextSeqS[F,A](head, functor.map(tail)(_.stream), stop))

    /** Promote a non-strict value representing a stream to a stream
      * of the same type, effectively delaying its initialisation.
      *
      * The suspension will act as a factory of streams, with any
      * described side-effects happening on each evaluation.
      */
    def suspend[A](fa: => Self[A]): Self[A] =
      fromStream(Iterant.suspend[F,A](fa.stream))

    /** Alias for [[Builders.suspendS[A](fa* suspend]]. */
    def defer[A](fa: => Self[A]): Self[A] =
      fromStream(Iterant.defer[F,A](fa.stream))

    /** $suspendDesc
      *
      * @param rest is the suspended stream
      */
    def suspend[A](rest: F[Self[A]]): Self[A] =
      fromStream(Iterant.suspend[F,A](rest.map(_.stream)))

    /** $suspendDesc
      *
      * @param rest is the suspended stream
      * @param stop $stopDesc
      */
    def suspendS[A](rest: F[Self[A]], stop: F[Unit]): Self[A] =
      fromStream(Iterant.suspendS[F,A](rest.map(_.stream), stop))

    /** Returns an empty stream. */
    def empty[A]: Self[A] = fromStream(Iterant.empty[F,A])

    /** Returns a stream that signals an error. */
    def raiseError[A](ex: Throwable): Self[A] =
      fromStream(Iterant.raiseError[F,A](ex))

    /** $haltDesc */
    def halt[A](ex: Option[Throwable]): Self[A] =
      fromStream(Iterant.halt[F,A](ex))

    /** Converts any Scala `collection.IndexedSeq` into a stream.
      *
      * @param xs is the reference to be converted to a stream
      */
    def fromIndexedSeq[A](xs: IndexedSeq[A]): Self[A] =
      fromStream(Iterant.fromIndexedSeq[F,A](xs))

    /** Converts any Scala `collection.IndexedSeq` into a stream.
      *
      * @param xs is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIndexedSeq[A](xs: IndexedSeq[A], batchSize: Int): Self[A] =
      fromStream(Iterant.fromIndexedSeq[F,A](xs, batchSize))

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

    /** $fromIterableDesc
      *
      * @param xs is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIterable[A](xs: Iterable[A], batchSize: Int): Self[A] =
      fromStream(Iterant.fromIterable[F,A](xs, batchSize))

    /** $fromIteratorDesc
      *
      * @param xs is the reference to be converted to a stream
      */
    def fromIterator[A](xs: Iterator[A]): Self[A] =
      fromStream(Iterant.fromIterator[F,A](xs))

    /** $fromIteratorDesc
      *
      * @param xs is the reference to be converted to a stream
      * @param batchSize $batchSizeDesc
      */
    def fromIterator[A](xs: Iterator[A], batchSize: Int): Self[A] =
      fromStream(Iterant.fromIterator[F,A](xs, batchSize))

    /** Type-class instances for [[Iterant.Like]] types. */
    implicit val typeClassInstances: TypeClassInstances =
      new TypeClassInstances

    /** Type-class instances for [[Iterant.Like]] types. */
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

private[interact] trait StreamInstances extends StreamInstances1 {
  /** Provides a [[monix.types.Suspendable Suspendable]] instance
    * for [[Iterant]].
    */
  implicit def suspendableInstance[F[_] : Suspendable]: SuspendableInstance[F] =
    new SuspendableInstance[F]()

  /** Provides a [[monix.types.Suspendable Suspendable]] instance
    * for [[Iterant]].
    */
  class SuspendableInstance[F[_]](implicit F: Suspendable[F])
    extends MonadEvalInstance[F]()(F.monadEval)
      with Suspendable.Instance[({type λ[+α] = Iterant[F,α]})#λ] {

    override def suspend[A](fa: => Iterant[F, A]): Iterant[F, A] =
      Iterant.suspend(fa)
  }
}

private[interact] trait StreamInstances1 extends StreamInstances0 {
  /** Provides a [[monix.types.MonadEval MonadEval]] instance
    * for [[Iterant]].
    */
  implicit def monadEvalInstance[F[_] : MonadEval]: MonadEvalInstance[F] =
    new MonadEvalInstance[F]()

  /** Provides a [[monix.types.MonadEval MonadEval]] instance
    * for [[Iterant]].
    */
  class MonadEvalInstance[F[_]](implicit F: MonadEval[F])
    extends MonadInstance[F]()(F.monad)
    with MonadEval.Instance[({type λ[+α] = Iterant[F,α]})#λ] {

    def eval[A](a: => A): Iterant[F, A] =
      Iterant.eval[F,A](a)
  }
}

private[interact] trait StreamInstances0 {
  /** Provides a [[monix.types.Monad]] instance for [[Iterant]]. */
  implicit def monadInstance[F[_] : Monad]: MonadInstance[F] =
    new MonadInstance[F]()

  /** Provides a [[monix.types.Monad]] instance for [[Iterant]]. */
  class MonadInstance[F[_]](implicit F: Monad[F])
    extends Monad.Instance[({type λ[+α] = Iterant[F,α]})#λ] {

    def pure[A](a: A): Iterant[F, A] =
      Iterant.pure[F,A](a)(F.applicative)
    def flatMap[A, B](fa: Iterant[F, A])(f: (A) => Iterant[F, B]): Iterant[F, B] =
      fa.flatMap(f)
    def map[A, B](fa: Iterant[F, A])(f: (A) => B): Iterant[F, B] =
      fa.map(f)(F.applicative)
    def map2[A, B, Z](fa: Iterant[F, A], fb: Iterant[F, B])(f: (A, B) => Z): Iterant[F, Z] =
      fa.flatMap(a => fb.map(b => f(a,b))(F.applicative))
    def ap[A, B](ff: Iterant[F, (A) => B])(fa: Iterant[F, A]): Iterant[F, B] =
      ff.flatMap(f => fa.map(a => f(a))(F.applicative))
  }
}