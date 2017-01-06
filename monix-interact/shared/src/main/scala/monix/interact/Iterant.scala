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
import scala.collection.immutable.LinearSeq
import scala.collection.mutable
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
  * is over, or a (head, rest) pair, very similar in spirit to
  * Scala's standard `List` or `Iterant`.
  *
  * The type is an ADT, meaning a composite of the following types:
  *
  *  - [[monix.interact.Iterant.Next Next]] which signals a single strict
  *    element, the `head` and a `rest` representing the rest of the stream
  *  - [[monix.interact.Iterant.NextSeq NextSeq]] is a variation on `Next`
  *    for signaling a whole strict batch of elements as the `cursor`,
  *    along with the `rest` representing the rest of the stream
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
      case Next(head, rest, stop) =>
        Next(head, rest.map(_.doOnStop(f)), stop.flatMap(_ => f))
      case NextSeq(cursor, rest, stop) =>
        NextSeq(cursor, rest.map(_.doOnStop(f)), stop.flatMap(_ => f))
      case Suspend(rest, stop) =>
        Suspend(rest.map(_.doOnStop(f)), stop.flatMap(_ => f))
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

      case ref @ NextSeq(cursor, rest, stop) =>
        try if (!cursor.moveNext())
          Suspend[F,B](rest.map(_.mapEval(f)), stop)
        else {
          val head = cursor.current
          val tail = F.pure(ref)
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

      case NextSeq(cursor, rest, stop) =>
        try NextSeq[F,B](cursor.map(f), rest.map(_.map(f)), stop)
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
    import F.{functor, applicative => A}
    this match {
      case Next(head, tail, stop) =>
        try f(head).doOnStop(stop) ++ tail.map(_.flatMap(f))
        catch { case NonFatal(ex) => signalError(ex, stop) }

      case ref @ NextSeq(cursor, rest, stop) =>
        try if (!cursor.moveNext()) {
          Suspend[F,B](rest.map(_.flatMap(f)), stop)
        }
        else {
          f(cursor.current).doOnStop(stop) ++
            A.eval(ref.flatMap(f))
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
  final def foldLeftL[S](seed: => S)(f: (S,A) => S)(implicit F: MonadError[F,Throwable]): F[S] = {
    import F.{applicative => A, monad => M}

    def loop(self: Iterant[F,A], state: S): F[S] = {
      def next(a: A, next: F[Iterant[F,A]], stop: F[Unit]): F[S] =
        try {
          val newState = f(state, a)
          next.flatMap(loop(_, newState))
        } catch {
          case NonFatal(ex) =>
            stop.flatMap(_ => F.raiseError(ex))
        }

      self match {
        case Halt(None) =>
          A.pure(state)
        case Halt(Some(ex)) =>
          F.raiseError(ex)
        case Next(a, tail, stop) =>
          next(a, tail, stop)
        case Suspend(rest, _) =>
          rest.flatMap(loop(_, state))
        case NextSeq(cursor, next, stop) =>
          try {
            val newState = cursor.foldLeft(state)(f)
            next.flatMap(loop(_, newState))
          } catch {
            case NonFatal(ex) =>
              stop.flatMap(_ => F.raiseError(ex))
          }
      }
    }

    val init = A.eval(seed).onErrorHandleWith(ex => onStop.flatMap(_ => F.raiseError(ex)))
    init.flatMap(a => loop(self, a))
  }

  /** Aggregates all elements in a `List` and preserves order. */
  final def toListL[B >: A](implicit F: MonadError[F,Throwable]): F[List[B]] = {
    val folded = foldLeftL(mutable.ListBuffer.empty[B]) { (acc, a) => acc += a }
    F.functor.map(folded)(_.toList)
  }

  private def signalError(ex: Throwable, stop: F[Unit])
    (implicit F: Applicative[F]): Iterant[F, Nothing] = {
    import F.functor
    val t = stop.map(_ => Iterant.haltS[F,Nothing](Some(ex)))
    Iterant.Suspend[F,Nothing](t, stop)
  }
}

/** Defines [[Iterant]] builders.
  *
  * @define nextDesc The [[monix.interact.Iterant.Next Next]] state
  *         of the [[Iterant]] represents a `head` / `rest`
  *         cons pair, where the `head` is a strict value.
  *
  *         Note the `head` being a strict value means that it is
  *         already known, whereas the `rest` is meant to be lazy and
  *         can have asynchronous behavior as well, depending on the `F`
  *         type used.
  *
  *         See [[monix.interact.Iterant.NextSeq NextSeq]]
  *         for a state where the head is a strict immutable list.
  *
  * @define nextSeqDesc The [[monix.interact.Iterant.NextSeq NextSeq]] state
  *         of the [[Iterant]] represents a `cursor` / `rest` cons pair,
  *         where the `cursor` is an [[Cursor iterator-like]] type that
  *         can generate a whole batch of elements.
  *
  *         Useful for doing buffering, or by giving it an empty cursor,
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
  *         evaluating the `rest` references multiple times might yield
  *         undesired effects.
  *
  * @define fromIterableDesc Converts a `scala.collection.Iterable`
  *         into a stream.
  *
  *         Note that the generated `Iterator` is side-effectful and
  *         evaluating the `rest` references multiple times might yield
  *         undesired effects.
  *
  * @define batchSizeDesc indicates the size of a streamed batch on each event
  *        (by means of [[Iterant.NextSeq]]) or no batching done if it is
  *        equal to 1
  *
  * @define suspendEvalDesc Promote a non-strict value representing a
  *         stream to a stream of the same type, effectively delaying its
  *         initialisation.
  */
object Iterant extends StreamInstances {
  /** Given a sequence of elements, builds a stream out of it. */
  def apply[F[_] : Applicative, A](elems: A*): Iterant[F,A] =
    fromList[F,A](elems.toList)

  /** Lifts a strict value into the stream context,
    * returning a stream of one element.
    */
  def now[F[_],A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
    nextS[F,A](a, F.pure(empty[F,A]), F.unit)

  /** Alias for [[now]]. */
  def pure[F[_],A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
    now[F,A](a)(F)

  /** Lifts a non-strict value into the stream context,
    * returning a stream of one element that is lazily
    * evaluated.
    */
  def eval[F[_],A](a: => A)(implicit F: Applicative[F]): Iterant[F,A] =
    Suspend(F.eval(nextS[F,A](a, F.pure(Halt(None)), F.unit)), F.unit)

  /** Builds a [[Iterant.Next]] stream state.
    *
    * $nextDesc
    *
    * @param head is the current element to be signaled
    * @param rest is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def nextS[F[_],A](head: A, rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    Next[F,A](head, rest, stop)

  /** Builds a [[Iterant.NextSeq]] stream state.
    *
    * $nextSeqDesc
    *
    * @param cursor is an [[Cursor iterator-like]] type that can generate
    *        elements by traversing a collection
    * @param rest is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  def nextSeqS[F[_],A](cursor: Cursor[A], rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    NextSeq[F,A](cursor, rest, stop)

  /** Builds a [[Iterant.Suspend]] stream state.
    *
    * $suspendDesc
    *
    * @param rest is the suspended stream
    * @param stop $stopDesc
    */
  def suspendS[F[_],A](rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    Suspend[F,A](rest, stop)

  /** Builds a [[Iterant.Halt]] stream state.
    *
    * $haltDesc
    */
  def haltS[F[_],A](ex: Option[Throwable]): Iterant[F,A] =
    Halt[F](ex)

  /** $suspendEvalDesc */
  def suspend[F[_], A](fa: => Iterant[F,A])(implicit F: Applicative[F]): Iterant[F,A] =
    suspend[F,A](F.eval(fa))

  /** Alias for [[Iterant.suspend[F[_],A](fa* suspend]]. */
  def defer[F[_] : Applicative, A](fa: => Iterant[F,A]): Iterant[F,A] =
    suspend(fa)

  /** Builds a [[Iterant.Suspend]] stream state.
    *
    * $suspendDesc
    *
    * @param rest is the suspended stream
    */
  def suspend[F[_],A](rest: F[Iterant[F,A]])(implicit F: Applicative[F]): Iterant[F,A] =
    suspendS[F,A](rest, F.unit)

  /** Returns an empty stream. */
  def empty[F[_],A]: Iterant[F,A] =
    Halt[F](None)

  /** Returns a stream that signals an error. */
  def raiseError[F[_],A](ex: Throwable): Iterant[F,A] =
    Halt[F](Some(ex))

  /** Converts any Scala `collection.immutable.LinearSeq`
    * into a stream.
    */
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val fa = F.eval(nextSeqS[F,A](Cursor.fromSeq(xs), F.pure(haltS[F,A](None)), F.unit))
    Suspend[F,A](fa, F.unit)
  }

  /** Converts any Scala `collection.IndexedSeq` into a stream.
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val fa = F.eval(nextSeqS[F,A](Cursor.fromIndexedSeq(xs), F.pure(empty), F.unit))
    Suspend[F,A](fa, F.unit)
  }

  /** Converts any `scala.collection.Seq` into a stream. */
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F,A] =
    xs match {
      case ref: LinearSeq[_] =>
        fromList[F,A](ref.asInstanceOf[LinearSeq[A]])
      case ref: IndexedSeq[_] =>
        fromIndexedSeq[F,A](ref.asInstanceOf[IndexedSeq[A]])
      case _ =>
        fromIterable(xs)
    }

  /** $fromIterableDesc
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIterable[F[_],A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val init = F.eval(xs.iterator)
    val stop = F.unit
    val rest = F.functor.map(init)(iterator => fromIterator[F,A](iterator))
    Suspend[F,A](rest, stop)
  }

  /** $fromIteratorDesc
    *
    * @param xs is the reference to be converted to a stream
    */
  def fromIterator[F[_], A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F,A] =
    NextSeq[F,A](Cursor.fromIterator(xs), F.pure(empty), F.unit)

  /** $nextDesc
    *
    * @param head is the current element being signaled
    * @param rest is the next state in the sequence that will
    *        produce the rest of the stream
    * @param stop $stopDesc
    */
  final case class Next[F[_],A](
    head: A,
    rest: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $nextSeqDesc
    *
    * @param cursor is an [[Cursor iterator-like]] type that can generate
    *        elements by traversing a collection
    * @param rest is the rest of the stream
    * @param stop $stopDesc
    */
  final case class NextSeq[F[_],A](
    cursor: Cursor[A],
    rest: F[Iterant[F,A]],
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
}

private[interact] trait StreamInstances extends StreamInstances0

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
    def suspend[A](fa: => Iterant[F, A]): Iterant[F, A] =
      Iterant.suspend(fa)(F.applicative)
    def eval[A](a: => A): Iterant[F, A] =
      Iterant.eval(a)(F.applicative)
  }
}