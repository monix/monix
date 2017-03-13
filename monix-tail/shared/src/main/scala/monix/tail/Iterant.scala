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

package monix.tail

import monix.eval.{Coeval, Task}
import monix.tail.internal.{IterantCollect, IterantFilter, IterantMapEval, IterantStop}
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
  * Scala's standard `List` or `Iterable`.
  *
  * The type is an ADT, meaning a composite of the following types:
  *
  *  - [[monix.tail.Iterant.Next Next]] which signals a single strict
  *    element, the `head` and a `rest` representing the rest of the stream
  *  - [[monix.tail.Iterant.NextSeq NextSeq]] is a variation on `Next`
  *    for signaling a whole strict batch of elements as a traversable
  *    [[scala.collection.Iterator Iterator]], along with the `rest`
  *    representing the rest of the stream
  *  - [[monix.tail.Iterant.NextGen NextGen]] is a variation on `Next`
  *    for signaling a whole batch of elements by means of an `Iterable`,
  *    along with the `rest` representing the rest of the stream
  *  - [[monix.tail.Iterant.Suspend Suspend]] is for suspending the
  *    evaluation of a stream
  *  - [[monix.tail.Iterant.Halt Halt]] represents an empty
  *    stream, signaling the end, either in success or in error
  *  - [[monix.tail.Iterant.Last Last]] represents a one-element
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
  * committed in Cats by Erik Osheim. It was also inspired by other
  * push-based streaming abstractions, like the `Iteratee` or
  * `IAsyncEnumerable`.
  *
  * @define functorParamDesc is the [[monix.types.Functor functor]]
  *         instance that controls the evaluation for our iterant for this operation.
  *         Note that if the source iterant is powered by [[monix.eval.Task Task]] or
  *         [[monix.eval.Coeval Coeval]] one such instance is globally available.
  *
  * @define applicativeParamDesc is the [[monix.types.Applicative applicative]]
  *         instance that controls the evaluation for our iterant for this operation.
  *         Note that if the source iterant is powered by [[monix.eval.Task Task]] or
  *         [[monix.eval.Coeval Coeval]] one such instance is globally available.
  *
  * @define monadParamDesc is the [[monix.types.Monad monad]]
  *         instance that controls the evaluation for our iterant for this operation.
  *         Note that if the source iterant is powered by [[monix.eval.Task Task]] or
  *         [[monix.eval.Coeval Coeval]] one such instance should be globally available.
  *
  * @define monadErrorParamDesc is the [[monix.types.MonadError MonadError]]
  *         instance that controls the evaluation for our iterant for this operation.
  *         Note that if the source iterant is powered by [[monix.eval.Task Task]] or
  *         [[monix.eval.Coeval Coeval]] one such instance is globally available.
  *
  * @tparam F is the monadic type that controls evaluation; note that it
  *         must be stack-safe in its `map` and `flatMap` operations
  *
  * @tparam A is the type of the elements produced by this Iterant
  */
sealed abstract class Iterant[F[_], A] extends Product with Serializable { self =>
  import Iterant._

  /** Builds a new iterant by applying a partial function to all elements of
    * the source on which the function is defined.
    *
    * @param pf the partial function that filters and maps the iterant
    * @param F $functorParamDesc
    * @tparam B the element type of the returned iterant.
    *
    * @return a new iterant resulting from applying the partial function
    *         `pf` to each element on which it is defined and collecting the results.
    *         The order of the elements is preserved.
    */
  final def collect[B](pf: PartialFunction[A,B])(implicit F: Applicative[F]): Iterant[F,B] =
    IterantCollect(this, pf)(F)

  /** Returns a computation that should be evaluated in
    * case the streaming must stop before reaching the end.
    *
    * This is useful to release any acquired resources,
    * like opened file handles or network sockets.
    *
    * @param F $applicativeParamDesc
    */
  final def earlyStop(implicit F: Applicative[F]): F[Unit] =
    IterantStop.earlyStop(this)(F)

  /** Given a routine make sure to execute it whenever
    * the consumer executes the current `stop` action.
    *
    * @param f is the function to execute on early stop
    * @param F $monadParamDesc
    */
  final def doOnEarlyStop(f: F[Unit])(implicit F: Monad[F]): Iterant[F, A] =
    IterantStop.doOnEarlyStop(this, f)(F)

  /** Returns a new enumerator in which `f` is scheduled to be executed
    * on [[Iterant.Halt halt]] or on [[earlyStop]].
    *
    * This would typically be used to release any resources acquired by
    * this enumerator.
    *
    * Note that [[doOnEarlyStop]] is subsumed under this operation, the
    * given `f` being evaluated on both reaching the end or canceling early.
    *
    * @param f is the function to execute on early stop
    * @param F $monadParamDesc
    */
  def doOnFinish(f: Option[Throwable] => F[Unit])(implicit F: Monad[F]): Iterant[F, A] =
    IterantStop.doOnFinish(this, f)(F)

  /** Filters the iterant by the given predicate function,
    * returning only those elements that match.
    *
    * @param p the predicate used to test elements.
    * @param F $functorParamDesc
    *
    * @return a new iterant consisting of all elements that satisfy the given
    *         predicate. The order of the elements is preserved.
    */
  final def filter(p: A => Boolean)(implicit F: Applicative[F]): Iterant[F, A] =
    IterantFilter(this, p)(F)


  /** Given a mapping function that returns a possibly lazy or asynchronous
    * result, applies it over the elements emitted by the stream.
    *
    * @param f is the mapping function that transforms the source
    * @param F $applicativeParamDesc
    */
  final def mapEval[B](f: A => F[B])(implicit F: Applicative[F]): Iterant[F, B] =
    IterantMapEval(this, f)(F)

  /** Returns a new stream by mapping the supplied function
    * over the elements of the source.
    *
    * @param f is the mapping function that transforms the source
    * @param F $functorParamDesc
    */
  final def map[B](f: A => B)(implicit F: Functor[F]): Iterant[F,B] = {
    try this match {
      case Next(head, tail, stop) =>
        Next[F,B](f(head), tail.map(_.map(f)), stop)

      case NextSeq(cursor, rest, stop) =>
        NextSeq[F,B](cursor.map(f), rest.map(_.map(f)), stop)

      case NextGen(gen, rest, stop) =>
        NextGen(gen.map(f), rest.map(_.map(f)), stop)

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.map(f)), stop)

      case Last(item) =>
        Last(f(item))

      case empty @ Halt(_) =>
        empty.asInstanceOf[Iterant[F, B]]
    }
    catch {
      case NonFatal(ex) => signalError(ex)
    }
  }

  /** Applies the function to the elements of the source and concatenates the results.
    *
    * @param f is the function mapping elements from the source to iterants
    * @param F $monadParamDesc
    */
  final def flatMap[B](f: A => Iterant[F,B])(implicit F: Monad[F]): Iterant[F,B] = {
    import F.{functor, applicative => A}

    @inline def concat(item: A, rest: F[Iterant[F, B]], stop: F[Unit]): Iterant[F, B] =
      f(item) match {
        case next @ (Next(_,_,_) | NextSeq(_,_,_) | NextGen(_,_,_) | Suspend(_,_)) =>
          next.doOnEarlyStop(stop) ++ Suspend(rest, stop)
        case Last(value) =>
          Next(value, rest, stop)
        case Halt(None) =>
          Suspend[F,B](rest, stop)
        case Halt(Some(ex)) =>
          self.signalError(ex)
      }

    @inline def evalNextSeq(ref: NextSeq[F, A], cursor: Iterator[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext) {
        Suspend[F, B](rest.map(_.flatMap(f)), stop)
      }
      else {
        val item = cursor.next()
        // If iterator is empty then we can skip a beat
        val tail = if (cursor.hasNext) A.eval(ref.flatMap(f)) else rest.map(_.flatMap(f))
        concat(item, tail, stop)
      }
    }

    try this match {
      case Next(item, rest, stop) =>
        concat(item, rest.map(_.flatMap(f)), stop)

      case ref @ NextSeq(cursor, rest, stop) =>
        evalNextSeq(ref, cursor, rest, stop)

      case Suspend(rest, stop) =>
        Suspend[F,B](rest.map(_.flatMap(f)), stop)

      case NextGen(gen, rest, stop) =>
        val cursor = gen.iterator
        val ref = NextSeq(cursor, rest, stop)
        evalNextSeq(ref, cursor, rest, stop)

      case Last(item) =>
        f(item)

      case empty @ Halt(_) =>
        empty.asInstanceOf[Iterant[F, B]]
    }
    catch {
      case NonFatal(ex) => signalError(ex)
    }
  }

  /** Given an `Iterant` that generates `Iterant` elements,
    * concatenates all the generated iterants.
    *
    * Equivalent with: `source.flatMap(x => x)`
    *
    * @param F $monadParamDesc
    */
  final def flatten[B](implicit ev: A <:< Iterant[F,B], F: Monad[F]): Iterant[F,B] =
    flatMap(x => x)

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => Iterant[F,B])(implicit F: Monad[F]): Iterant[F,B] =
    flatMap(f)

  /** Alias for [[concat]]. */
  final def concat[B](implicit ev: A <:< Iterant[F,B], F: Monad[F]): Iterant[F,B] =
    flatten

  /** Appends the given stream to the end of the source, effectively concatenating them.
    *
    * @param rhs is the iterant to append at the end of our source
    * @param F $applicativeParamDesc
    */
  final def ++[B >: A](rhs: Iterant[F,B])(implicit F: Applicative[F]): Iterant[F,B] = {
    import F.functor
    this match {
      case Next(a, lt, stop) =>
        Next[F,B](a, lt.map(_ ++ rhs), stop)
      case NextSeq(seq, lt, stop) =>
        NextSeq[F,B](seq, lt.map(_ ++ rhs), stop)
      case NextGen(gen, rest, stop) =>
        NextGen(gen, rest.map(_ ++ rhs), stop)
      case Suspend(lt, stop) =>
        Suspend[F,B](lt.map(_ ++ rhs), stop)
      case Last(item) =>
        Next[F,B](item, F.pure(rhs), rhs.earlyStop)
      case Halt(None) =>
        rhs
      case error @ Halt(Some(_)) =>
        error.asInstanceOf[Iterant[F, B]]
    }
  }

  /** Prepends an element to the enumerator. */
  final def #::(head: A)(implicit F: Applicative[F]): Iterant[F, A] =
    Next(head, F.pure(this), earlyStop)

  /** Left associative fold using the function `f`.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    *
    * @param seed is the start value
    * @param op is the binary operator
    * @param F $monadParamDesc
    *
    * @return the result of inserting `op` between consecutive elements
    *         of this iterant, going from left to right with the
    *         `seed` as the start value, or `seed` if the iterant
    *         is empty.
    */
  final def foldLeftL[S](seed: => S)(op: (S,A) => S)(implicit F: Monad[F]): F[S] = {
    import F.{applicative => A}

    def loop(self: Iterant[F, A], state: S): F[S] = {
      try self match {
        case Next(a, rest, stop) =>
          val newState = op(state, a)
          rest.flatMap(loop(_, newState))
        case NextSeq(cursor, rest, stop) =>
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(_, newState))
        case NextGen(gen, rest, stop) =>
          val newState = gen.iterator.foldLeft(state)(op)
          rest.flatMap(loop(_, newState))
        case Suspend(rest, stop) =>
          rest.flatMap(loop(_, state))
        case Last(item) =>
          A.pure(op(state,item))
        case Halt(None) =>
          A.pure(state)
        case Halt(Some(ex)) =>
          throw ex
      }
      catch {
        case NonFatal(ex) =>
          earlyStop.flatMap(_ => (throw ex) : F[S])
      }
    }

    F.suspend {
      var catchErrors = true
      try {
        val init = seed
        catchErrors = false
        loop(self, init)
      }
      catch {
        case NonFatal(ex) if catchErrors =>
          earlyStop.flatMap(_ => (throw ex) : F[S])
      }
    }
  }

  /** Aggregates all elements in a `List` and preserves order.
    *
    * @param F $monadParamDesc
    */
  final def toListL[B >: A](implicit F: Monad[F]): F[List[B]] = {
    val folded = foldLeftL(mutable.ListBuffer.empty[B]) { (acc, a) => acc += a }
    F.functor.map(folded)(_.toList)
  }

  final def upcast[B >: A]: Iterant[F, B] =
    this.asInstanceOf[Iterant[F, B]]

  private def signalError[B](ex: Throwable)(implicit F: Functor[F]): Iterant[F,B] = {
    val halt = Iterant.haltS[F,B](Some(ex))
    this match {
      case Next(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case NextSeq(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case NextGen(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case Suspend(_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case Last(_) | Halt(_) =>
        halt
    }
  }
}

/** Defines the standard [[Iterant]] builders. */
object Iterant extends IterantInstances with SharedDocs {
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
  def now[F[_], A](a: A): Iterant[F, A] =
    lastS(a)

  /** Alias for [[now]]. */
  def pure[F[_], A](a: A): Iterant[F, A] =
    now[F,A](a)

  /** $builderEval */
  def eval[F[_], A](a: => A)(implicit F: Applicative[F]): Iterant[F, A] =
    Suspend(F.eval(nextS[F,A](a, F.pure(Halt(None)), F.unit)), F.unit)

  /** $nextSDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[F[_], A](item: A, rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Next[F,A](item, rest, stop)

  /** $nextSeqSDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextSeqS[F[_], A](items: Iterator[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    NextSeq[F,A](items, rest, stop)

  /** $nextGenSDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextGenS[F[_], A](items: Iterable[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    NextGen[F,A](items, rest, stop)

  /** $suspendSDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[F[_], A](rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Suspend[F,A](rest, stop)

  /** $lastSDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[F[_], A](item: A): Iterant[F, A] =
    Last(item)

  /** $haltSDesc
    *
    * @param ex $exParamDesc
    */
  def haltS[F[_], A](ex: Option[Throwable]): Iterant[F, A] =
    Halt[F, A](ex)

  /** $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def suspend[F[_], A](fa: => Iterant[F, A])(implicit F: Applicative[F]): Iterant[F, A] =
    suspend[F,A](F.eval(fa))

  /** Alias for [[Iterant.suspend[F[_], A](fa* suspend]].
    *
    * $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def defer[F[_] : Applicative, A](fa: => Iterant[F, A]): Iterant[F, A] =
    suspend(fa)

  /** $builderSuspendByF
    *
    * @param rest $restParamDesc
    */
  def suspend[F[_], A](rest: F[Iterant[F, A]])(implicit F: Applicative[F]): Iterant[F, A] =
    suspendS[F,A](rest, F.unit)

  /** $builderEmpty */
  def empty[F[_], A]: Iterant[F, A] =
    Halt[F, A](None)

  /** $builderRaiseError */
  def raiseError[F[_], A](ex: Throwable): Iterant[F, A] =
    Halt[F, A](Some(ex))

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
  def fromArray[F[_], A](xs: Array[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextGen(xs, F.pure(empty[F,A]), F.unit)

  /** $builderFromList */
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextGen(xs, F.pure(empty[F,A]), F.unit)

  /** $builderFromIndexedSeq */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextGen(xs, F.pure(empty[F,A]), F.unit)

  /** $builderFromSeq */
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    xs match {
      case ref: LinearSeq[_] =>
        fromList[F,A](ref.asInstanceOf[LinearSeq[A]])
      case ref: IndexedSeq[_] =>
        fromIndexedSeq[F,A](ref.asInstanceOf[IndexedSeq[A]])
      case _ =>
        fromIterable(xs)
    }

  /** $builderFromIterable */
  def fromIterable[F[_], A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextGen(xs, F.pure(empty[F,A]), F.unit)

  /** $builderFromIterator */
  def fromIterator[F[_], A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextSeq[F,A](xs, F.pure(empty), F.unit)

  /** $builderRange
    *
    * @param from $rangeFromParam
    * @param until $rangeUntilParam
    * @param step $rangeStepParam
    * @return $rangeReturnDesc
    */
  def range[F[_]](from: Int, until: Int, step: Int = 1)(implicit F: Applicative[F]): Iterant[F,Int] =
    NextGen(Iterable.range(from, until, step), F.pure(empty[F,Int]), F.unit)

  /** $NextDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Next[F[_], A](
    item: A,
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A]

  /** $LastDesc
    *
    * @param item $lastParamDesc
    */
  final case class Last[F[_], A](item: A)
    extends Iterant[F, A]

  /** $NextSeqDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextSeq[F[_], A](
    items: Iterator[A],
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A]

  /** $NextGenDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextGen[F[_], A](
    items: Iterable[A],
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A]

  /** $SuspendDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Suspend[F[_], A](
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A]

  /** $HaltDesc
    *
    * @param ex $exParamDesc
    */
  final case class Halt[F[_], A](ex: Option[Throwable])
    extends Iterant[F, A]
}

private[tail] trait IterantInstances extends IterantInstances1 {
  /** Provides type-class instances for `Iterant[Task, +A]`, based
    * on the default instances provided by
    * [[monix.eval.Task.typeClassInstances Task.typeClassInstances]].
    */
  implicit def iterantTaskInstances(implicit F: Task.TypeClassInstances): IterantTaskInstances = {
    import Task.{nondeterminism, typeClassInstances => default}
    // Avoiding the creation of junk, because it is expensive
    F match {
      case `default` => defaultIterantTaskRef
      case `nondeterminism` => nondetIterantTaskRef
      case _ => new IterantTaskInstances()(F)
    }
  }

  /** Provides type-class instances for `Iterant[Task, +A]`, based
    * on the default instances provided by
    * [[monix.eval.Task.TypeClassInstances Task.TypeClassInstances]].
    */
  class IterantTaskInstances(implicit F: Task.TypeClassInstances)
    extends MonadInstance[Task]()(F)

  /** Provides type-class instances for `Iterant[Coeval, +A]`, based on
    * the default instances provided by
    * [[monix.eval.Coeval.typeClassInstances Coeval.typeClassInstances]].
    */
  implicit def iterantCoevalInstances(implicit F: Coeval.TypeClassInstances): IterantCoevalInstances = {
    import Coeval.{typeClassInstances => default}
    // Avoiding the creation of junk, because it is expensive
    F match {
      case `default` => defaultIterantCoevalRef
      case _ => new IterantCoevalInstances()(F)
    }
  }

  /** Provides type-class instances for `Iterant[Coeval, +A]`, based on
    * the default instances provided by
    * [[monix.eval.Coeval.TypeClassInstances Coeval.TypeClassInstances]].
    */
  class IterantCoevalInstances(implicit F: Coeval.TypeClassInstances)
    extends MonadInstance[Coeval]()(F)

  /** Reusable instance for `Iterant[Task, A]`, avoids creating junk. */
  private[this] val nondetIterantTaskRef =
    new IterantTaskInstances()(Task.nondeterminism)

  /** Reusable instance for `Iterant[Task, A]`, avoids creating junk. */
  private[this] final val defaultIterantTaskRef =
    new IterantTaskInstances()(Task.typeClassInstances)

  /** Reusable instance for `Iterant[Coeval, A]`, avoids creating junk. */
  private[this] final val defaultIterantCoevalRef =
    new IterantCoevalInstances()(Coeval.typeClassInstances)
}

private[tail] trait IterantInstances1 extends IterantInstances0 {
  /** Provides a [[monix.types.Monad]] instance for [[Iterant]]. */
  implicit def monadInstance[F[_] : Monad]: MonadInstance[F] =
    new MonadInstance[F]()

  /** Provides a [[monix.types.Monad]] instance for [[Iterant]]. */
  class MonadInstance[F[_]](implicit F: Monad[F])
    extends FunctorInstance[F]()(F.functor)
    with Monad.Instance[({type λ[α] = Iterant[F,α]})#λ]
    with MonadRec.Instance[({type λ[α] = Iterant[F,α]})#λ]
    with MonadFilter.Instance[({type λ[α] = Iterant[F,α]})#λ]
    with MonoidK.Instance[({type λ[α] = Iterant[F,α]})#λ] {

    def flatMap[A, B](fa: Iterant[F, A])(f: (A) => Iterant[F, B]): Iterant[F, B] =
      fa.flatMap(f)
    def suspend[A](fa: => Iterant[F, A]): Iterant[F, A] =
      Iterant.suspend(fa)(F.applicative)
    def pure[A](a: A): Iterant[F, A] =
      Iterant.pure(a)
    def map2[A, B, Z](fa: Iterant[F, A], fb: Iterant[F, B])(f: (A, B) => Z): Iterant[F, Z] =
      fa.flatMap(a => fb.map(b => f(a,b))(F.functor))
    def ap[A, B](ff: Iterant[F, (A) => B])(fa: Iterant[F, A]): Iterant[F, B] =
      ff.flatMap(f => fa.map(a => f(a))(F.functor))
    def eval[A](a: => A): Iterant[F, A] =
      Iterant.eval(a)(F.applicative)
    def tailRecM[A, B](a: A)(f: (A) => Iterant[F, Either[A, B]]): Iterant[F, B] =
      Iterant.tailRecM(a)(f)(F)
    def empty[A]: Iterant[F, A] =
      Iterant.empty
    def filter[A](fa: Iterant[F, A])(f: (A) => Boolean): Iterant[F, A] =
      fa.filter(f)(F.applicative)
    def combineK[A](x: Iterant[F, A], y: Iterant[F, A]): Iterant[F, A] =
      x.++(y)(F.applicative)
  }
}

private[tail] trait IterantInstances0 {
  /** Provides a [[monix.types.Functor]] instance for [[Iterant]]. */
  implicit def functorInstance[F[_] : Functor]: FunctorInstance[F] =
    new FunctorInstance[F]()

  /** Provides a [[monix.types.Functor]] instance for [[Iterant]]. */
  class FunctorInstance[F[_]](implicit F: Functor[F])
    extends Functor.Instance[({type λ[α] = Iterant[F,α]})#λ] {

    def map[A, B](fa: Iterant[F, A])(f: (A) => B): Iterant[F, B] =
      fa.map(f)(F)
  }
}