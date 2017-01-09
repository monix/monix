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
  *  - [[monix.interact.Iterant.Last Last]] represents a one-element
  *    stream, where `Last(item)` as an optimisation on
  *    `Next(item, F.pure(Halt(None)), F.unit)`
  *
  * The `Iterant` type accepts as type parameter an `F` monadic type
  * that is used to control how evaluation happens. For example you can
  * use [[monix.eval.Task Task]], in which case the streaming can have
  * asynchronous behavior, or you can use [[monix.eval.Coeval Coeval]]
  * in which case it can behave like a normal, synchronous `Iterable`.
  *
  * As restriction, this `F[_]` type used should be stack safe in
  * `map` and `flatMap`, otherwise you might get stack-overflow
  * exceptions.
  *
  * ATTRIBUTION: this type was inspired by the `Streaming` type in the
  * Typelevel Cats library (later moved to Typelevel's Dogs), originally
  * committed in Cats by Erik Osheim.
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
      case Last(_) => F.unit
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
      case ref @ (Halt(_) | Last(_)) =>
        ref // nothing to do
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
          val fa = f(head)
          // If the cursor is empty, then we can skip a beat
          val tail = if (cursor.hasMore()) F.pure(ref : Iterant[F,A]) else rest
          val suspended = fa.map(h => nextS(h, tail.map(_.mapEval(f)), stop))
          Suspend[F,B](suspended, stop)
        }
        catch { case NonFatal(ex) =>
          signalError(ex, stop)
        }

      case Last(item) =>
        try {
          val fa = f(item)
          Suspend(fa.map(h => lastS[F,B](h)), F.unit)
        } catch {
          case NonFatal(ex) => signalError(ex, F.unit)
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

      case Last(item) =>
        try Last(f(item)) catch { case NonFatal(ex) => signalError(ex, F.unit) }

      case empty @ Halt(_) =>
        empty
    }
  }

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => Iterant[F,B])(implicit F: Monad[F]): Iterant[F,B] = {
    import F.{functor, applicative => A}

    @inline def concat(item: A, rest: F[Iterant[F, B]], stop: F[Unit]): Iterant[F, B] = {
      try f(item) match {
        case next @ (Next(_,_,_) | NextSeq(_,_,_) | Suspend(_,_)) =>
          next.doOnStop(stop) ++ Suspend(rest, stop)
        case Last(value) =>
          Next(value, rest, stop)
        case Halt(None) =>
          Suspend[F,B](rest, stop)
        case Halt(Some(ex)) =>
          signalError(ex, stop)
      }
      catch {
        case NonFatal(ex) => signalError(ex, stop)
      }
    }

    this match {
      case Next(item, rest, stop) =>
        concat(item, rest.map(_.flatMap(f)), stop)

      case ref @ NextSeq(cursor, rest, stop) =>
        try if (!cursor.moveNext()) {
          Suspend[F,B](rest.map(_.flatMap(f)), stop)
        }
        else {
          val item = cursor.current
          // If cursor is empty then we can skip a beat
          val tail = if (cursor.hasMore()) A.eval(ref.flatMap(f)) else rest.map(_.flatMap(f))
          concat(item, tail, stop)
        }
        catch {
          case NonFatal(ex) => signalError(ex, stop)
        }

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.flatMap(f)), stop)

      case Last(item) =>
        try f(item) catch { case NonFatal(ex) => signalError(ex, A.unit) }

      case empty @ Halt(_) =>
        empty
    }
  }

  /** Appends the given stream to the end of the source,
    * effectively concatenating them.
    */
  final def ++[B >: A](rhs: Iterant[F,B])(implicit F: Applicative[F]): Iterant[F,B] = {
    import F.functor
    this match {
      case Next(a, lt, stop) =>
        val rest = lt.map(_ ++ rhs)
        Next[F,B](a, rest, stop)
      case NextSeq(seq, lt, stop) =>
        val rest = lt.map(_ ++ rhs)
        NextSeq[F,B](seq, rest, stop)
      case Suspend(lt, stop) =>
        val rest = lt.map(_ ++ rhs)
        Suspend[F,B](rest, stop)
      case Last(item) =>
        Next[F,B](item, F.pure(rhs), rhs.onStop)
      case Halt(None) =>
        rhs
      case error @ Halt(Some(_)) =>
        error
    }
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
        case Next(a, tail, stop) =>
          next(a, tail, stop)
        case NextSeq(cursor, next, stop) =>
          try {
            val newState = cursor.foldLeft(state)(f)
            next.flatMap(loop(_, newState))
          } catch {
            case NonFatal(ex) =>
              stop.flatMap(_ => F.raiseError(ex))
          }
        case Suspend(rest, _) =>
          rest.flatMap(loop(_, state))
        case Last(item) =>
          try A.pure(f(state,item)) catch { case NonFatal(ex) => F.raiseError(ex) }
        case Halt(None) =>
          A.pure(state)
        case Halt(Some(ex)) =>
          F.raiseError(ex)
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

/** Defines the standard [[Iterant]] builders. */
object Iterant extends StreamInstances with SharedDocs {
  /** Returns an [[IterantBuilders]] instance for the
    * specified `F` monadic type that can be used to build
    * [[Iterant]] instances.
    *
    * Example:
    * {{{
    *   Iterant[Task].range(0, 10)
    * }}}
    */
  def apply[F[_]](implicit F: IterantBuilders.From[F]): F.Builders = F.instance

  /** $builderNow */
  def now[F[_],A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
    lastS(a)

  /** Alias for [[now]]. */
  def pure[F[_],A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
    now[F,A](a)(F)

  /** $builderEval */
  def eval[F[_],A](a: => A)(implicit F: Applicative[F]): Iterant[F,A] =
    Suspend(F.eval(nextS[F,A](a, F.pure(Halt(None)), F.unit)), F.unit)

  /** $nextSDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[F[_],A](item: A, rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    Next[F,A](item, rest, stop)

  /** $nextSeqSDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextSeqS[F[_],A](items: Cursor[A], rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    NextSeq[F,A](items, rest, stop)

  /** $suspendSDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[F[_],A](rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
    Suspend[F,A](rest, stop)

  /** $lastSDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[F[_],A](item: A): Iterant[F,A] =
    Last(item)

  /** $haltSDesc
    *
    * @param ex $exParamDesc
    */
  def haltS[F[_],A](ex: Option[Throwable]): Iterant[F,A] =
    Halt[F](ex)

  /** $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def suspend[F[_], A](fa: => Iterant[F,A])(implicit F: Applicative[F]): Iterant[F,A] =
    suspend[F,A](F.eval(fa))

  /** Alias for [[Iterant.suspend[F[_],A](fa* suspend]].
    *
    * $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def defer[F[_] : Applicative, A](fa: => Iterant[F,A]): Iterant[F,A] =
    suspend(fa)

  /** $builderSuspendByF
    *
    * @param rest $restParamDesc
    */
  def suspend[F[_],A](rest: F[Iterant[F,A]])(implicit F: Applicative[F]): Iterant[F,A] =
    suspendS[F,A](rest, F.unit)

  /** $builderEmpty */
  def empty[F[_],A]: Iterant[F,A] =
    Halt[F](None)

  /** $builderRaiseError */
  def raiseError[F[_],A](ex: Throwable): Iterant[F,A] =
    Halt[F](Some(ex))

  /** $builderTailRecM */
  def tailRecM[F[_], A, B](a: A)(f: A => Iterant[F,Either[A, B]])(implicit F: Monad[F]): Iterant[F,B] = {
    import F.applicative
    f(a).flatMap {
      case Right(b) =>
        Iterant.now[F,B](b)
      case Left(nextA) =>
        suspend(tailRecM(nextA)(f))
    }
  }

  /** $builderFromArray */
  def fromArray[F[_], A](xs: Array[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val fa = F.eval(nextSeqS[F,A](Cursor.fromArray(xs), F.pure(haltS[F,A](None)), F.unit))
    Suspend[F,A](fa, F.unit)
  }

  /** $builderFromList */
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val fa = F.eval(nextSeqS[F,A](Cursor.fromSeq(xs), F.pure(haltS[F,A](None)), F.unit))
    Suspend[F,A](fa, F.unit)
  }

  /** $builderFromIndexedSeq */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val fa = F.eval(nextSeqS[F,A](Cursor.fromIndexedSeq(xs), F.pure(empty), F.unit))
    Suspend[F,A](fa, F.unit)
  }

  /** $builderFromSeq */
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F,A] =
    xs match {
      case ref: LinearSeq[_] =>
        fromList[F,A](ref.asInstanceOf[LinearSeq[A]])
      case ref: IndexedSeq[_] =>
        fromIndexedSeq[F,A](ref.asInstanceOf[IndexedSeq[A]])
      case _ =>
        fromIterable(xs)
    }

  /** $builderFromIterable */
  def fromIterable[F[_],A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F,A] = {
    val init = F.eval(xs.iterator)
    val stop = F.unit
    val rest = F.functor.map(init)(iterator => fromIterator[F,A](iterator))
    Suspend[F,A](rest, stop)
  }

  /** $builderFromIterator */
  def fromIterator[F[_], A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F,A] =
    NextSeq[F,A](Cursor.fromIterator(xs), F.pure(empty), F.unit)

  /** $builderRange
    *
    * @param from $rangeFromParam
    * @param until $rangeUntilParam
    * @param step $rangeStepParam
    * @return $rangeReturnDesc
    */
  def range[F[_]](from: Int, until: Int, step: Int = 1)(implicit F: Applicative[F]): Iterant[F,Int] = {
    val fa = F.eval(nextSeqS[F,Int](Cursor.range(from, until, step), F.pure(Halt(None)), F.unit))
    Suspend[F,Int](fa, F.unit)
  }

  /** $NextDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Next[F[_],A](
    item: A,
    rest: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $LastDesc
    *
    * @param item $lastParamDesc
    */
  final case class Last[F[_],A](item: A)
    extends Iterant[F,A]

  /** $NextSeqDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextSeq[F[_],A](
    items: Cursor[A],
    rest: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $SuspendDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Suspend[F[_], A](
    rest: F[Iterant[F,A]],
    stop: F[Unit])
    extends Iterant[F,A]

  /** $HaltDesc
    *
    * @param ex $exParamDesc
    */
  final case class Halt[F[_]](ex: Option[Throwable])
    extends Iterant[F,Nothing]
}

private[interact] trait StreamInstances extends StreamInstances0 {
  /** Provides a [[monix.types.MonadRec]] instance for [[Iterant]]. */
  implicit def monadRecInstance[F[_] : Monad]: MonadInstance[F] =
    new MonadRecInstance[F]()

  /** Provides a [[monix.types.MonadRec]] instance for [[Iterant]]. */
  class MonadRecInstance[F[_]](implicit F: Monad[F]) extends MonadInstance[F]
    with MonadRec.Instance[({type λ[+α] = Iterant[F,α]})#λ] {

    def tailRecM[A, B](a: A)(f: (A) => Iterant[F, Either[A, B]]): Iterant[F, B] =
      Iterant.tailRecM(a)(f)(F)
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
    def suspend[A](fa: => Iterant[F, A]): Iterant[F, A] =
      Iterant.suspend(fa)(F.applicative)
    def eval[A](a: => A): Iterant[F, A] =
      Iterant.eval(a)(F.applicative)
  }
}