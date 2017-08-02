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

import cats.effect.Sync
import cats.{Applicative, Comonad, Monad, MonoidK}
import monix.eval.instances.{CatsAsyncInstances, CatsSyncInstances}
import monix.eval.{Coeval, Task}
import monix.tail.batches.{Batch, BatchCursor}
import monix.tail.internal._
import scala.collection.immutable.LinearSeq
import scala.reflect.ClassTag

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
  *
  *  - [[monix.tail.Iterant.NextCursor NextCursor]] is a variation on `Next`
  *    for signaling a whole strict batch of elements as a traversable
  *    [[scala.collection.Iterator Iterator]], along with the `rest`
  *    representing the rest of the stream
  *
  *  - [[monix.tail.Iterant.NextBatch NextBatch]] is a variation on `Next`
  *    for signaling a whole batch of elements by means of an
  *    `Iterable`, along with the `rest` representing the rest of the
  *    stream
  *
  *  - [[monix.tail.Iterant.Suspend Suspend]] is for suspending the
  *    evaluation of a stream
  *
  *  - [[monix.tail.Iterant.Halt Halt]] represents an empty stream,
  *    signaling the end, either in success or in error
  *
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
  * @define foldLeftDesc Left associative fold using the function `f`.
  *
  *         On execution the stream will be traversed from left to
  *         right, and the given function will be called with the
  *         prior result, accumulating state until the end, when the
  *         summary is returned.
  * 
  * @define foldLeftReturnDesc the result of inserting `op` between consecutive
  *         elements of this iterant, going from left to right with
  *         the `seed` as the start value, or `seed` if the iterant
  *         is empty.
  *
  * @define strictVersionDesc for the strict (immediate, synchronous)
  *         version, assuming the `F[_]` type allows it (has a `Comonad`
  *         implementation)
  *
  * @define lazyVersionDesc for the lazy version (that doesn't pull
  *         values out of the evaluation context)
  *
  * @tparam F is the data type that controls evaluation; note that
  *         it must be stack-safe in its `map` and `flatMap`
  *         operations
  *
  * @tparam A is the type of the elements produced by this Iterant
  */
sealed abstract class Iterant[F[_], A] extends Product with Serializable {
  self =>

  import Iterant._

  /** Appends the given stream to the end of the source, effectively
    * concatenating them.
    *
    * @param rhs is the iterant to append at the end of our source
    */
  final def ++[B >: A](rhs: Iterant[F, B])(implicit F: Applicative[F]): Iterant[F, B] =
    IterantConcat.concat(this.upcast[B], rhs)(F)

  /** Appends a stream to the end of the source, effectively
    * concatenating them.
    *
    * @param rhs is the iterant to append at the end of our source
    */
  final def ++[B >: A](rhs: F[Iterant[F, B]])(implicit F: Applicative[F]): Iterant[F, B] =
    IterantConcat.concat(self.upcast[B], Suspend(rhs, F.unit))

  /** Prepends an element to the enumerator. */
  final def #::[B >: A](head: B)(implicit F: Applicative[F]): Iterant[F, B] =
    Next(head, F.pure(self.upcast[B]), earlyStop)

  /** Builds a new iterant by applying a partial function to all
    * elements of the source on which the function is defined.
    *
    * @param pf the partial function that filters and maps the iterant
    * @tparam B the element type of the returned iterant.
    *
    * @return a new iterant resulting from applying the partial
    *         function `pf` to each element on which it is defined and
    *         collecting the results.  The order of the elements is
    *         preserved.
    */
  final def collect[B](pf: PartialFunction[A, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantCollect(this, pf)(F)

  /** Consumes the source iterable.
    *
    * @see [[completeS]] $strictVersionDesc
    */
  final def completeL(implicit F: Sync[F]): F[Unit] =
    IterantCompleteL(this)(F)

  /** Consumes the source iterable.
    *
    * @see [[completeL]] $lazyVersionDesc
    */
  final def completeS(implicit F: Sync[F], C: Comonad[F]): Unit =
    C.extract(completeL(F))

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    flatMap(f)

  /** Given a routine make sure to execute it whenever
    * the consumer executes the current `stop` action.
    *
    * @param f is the function to execute on early stop
    */
  final def doOnEarlyStop(f: F[Unit])(implicit F: Sync[F]): Iterant[F, A] =
    IterantStop.doOnEarlyStop(this, f)(F)

  /** Returns a new enumerator in which `f` is scheduled to be executed
    * on [[Iterant.Halt halt]] or on [[earlyStop]].
    *
    * This would typically be used to release any resources acquired
    * by this enumerator.
    *
    * Note that [[doOnEarlyStop]] is subsumed under this operation,
    * the given `f` being evaluated on both reaching the end or
    * canceling early.
    *
    * @param f is the function to execute on early stop
    */
  final def doOnFinish(f: Option[Throwable] => F[Unit])(implicit F: Sync[F]): Iterant[F, A] =
    IterantStop.doOnFinish(this, f)(F)

  /** Drops the first `n` elements (from the start).
    *
    * @param n the number of elements to drop
    * @return a new iterant that drops the first ''n'' elements
    *         emitted by the source
    */
  final def drop(n: Int)(implicit F: Sync[F]): Iterant[F, A] =
    IterantDrop(self, n)(F)

  /** Drops the longest prefix of elements that satisfy the given
    * predicate and returns a new iterant that emits the rest.
    */
  final def dropWhile(p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] =
    IterantDropWhile(self, p)

  /** Returns a computation that should be evaluated in case the
    * streaming must stop before reaching the end.
    *
    * This is useful to release any acquired resources, like opened
    * file handles or network sockets.
    */
  def earlyStop(implicit F: Applicative[F]): F[Unit]

  /** Filters the iterant by the given predicate function, returning
    * only those elements that match.
    *
    * @param p the predicate used to test elements.
    *
    * @return a new iterant consisting of all elements that satisfy the given
    *         predicate. The order of the elements is preserved.
    */
  final def filter(p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] =
    IterantFilter(this, p)(F)

  /** Consumes the source iterable, executing the given callback for
    * each element.
    *
    * @see [[foreachS]] $strictVersionDesc
    */
  final def foreachL(cb: A => Unit)(implicit F: Sync[F]): F[Unit] =
    map(cb)(F).completeL

  /** Consumes the source iterable, executing the given callback for
    * each element.
    *
    * @see [[foreachL]] $lazyVersionDesc
    */
  final def foreachS(cb: A => Unit)(implicit F: Sync[F], C: Comonad[F]): Unit =
    C.extract(foreachL(cb))

  /** Optionally selects the first element.
    *
    * @see [[headOptionL]] $lazyVersionDesc
    *
    * @return the first element of this iterant if it is nonempty, or
    *         `None` if it is empty, in the `F` context.
    */
  final def headOptionS(implicit F: Sync[F], C: Comonad[F]): Option[A] =
    C.extract(IterantSlice.headOptionL(self)(F))

  /** Optionally selects the first element.
    *
    * @see [[headOptionS]] $strictVersionDesc
    *
    * @return the first element of this iterant if it is nonempty, or
    *         `None` if it is empty, in the `F` context.
    */
  final def headOptionL(implicit F: Sync[F]): F[Option[A]] =
    IterantSlice.headOptionL(self)(F)

  /** Returns a new stream by mapping the supplied function over the
    * elements of the source.
    *
    * @param f is the mapping function that transforms the source
    */
  final def map[B](f: A => B)(implicit F: Sync[F]): Iterant[F, B] =
    IterantMap(this, f)(F)

  /** Given a mapping function that returns a possibly lazy or
    * asynchronous result, applies it over the elements emitted by the
    * stream.
    *
    * @param f is the mapping function that transforms the source
    */
  final def mapEval[B](f: A => F[B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantMapEval(this, f)(F)

  /** Applies the function to the elements of the source and
    * concatenates the results.
    *
    * @param f is the function mapping elements from the source to iterants
    */
  final def flatMap[B](f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantConcat.flatMap(this, f)(F)

  /** Alias for [[concat]]. */
  final def concat[B](implicit ev: A <:< Iterant[F, B], F: Sync[F]): Iterant[F, B] =
    flatten(ev, F)

  /** Given an `Iterant` that generates `Iterant` elements, concatenates
    * all the generated iterants.
    *
    * Equivalent with: `source.flatMap(x => x)`
    */
  final def flatten[B](implicit ev: A <:< Iterant[F, B], F: Sync[F]): Iterant[F, B] =
    flatMap(x => x)(F)

  /** $foldLeftDesc
    *
    * @param seed is the start value
    * @param op is the binary operator
    *
    * @see [[foldLeftL]] $lazyVersionDesc
    *
    * @return $foldLeftReturnDesc
    */
  final def foldLeftS[S](seed: => S)(op: (S, A) => S)
    (implicit F: Sync[F], C: Comonad[F]): S =
    C.extract(IterantFoldLeftL(self, seed)(op)(F))

  /** $foldLeftDesc
    *
    * @param seed is the start value
    * @param op is the binary operator
    *
    * @see [[foldLeftS]] $strictVersionDesc
    *
    * @return $foldLeftReturnDesc
    */
  final def foldLeftL[S](seed: => S)(op: (S, A) => S)(implicit F: Sync[F]): F[S] =
    IterantFoldLeftL(self, seed)(op)(F)

  /** Applies the function to the elements of the source and
    * concatenates the results.
    *
    * This variant of [[flatMap]] is not referentially transparent,
    * because it tries to apply function `f` immediately, in case the
    * `Iterant` is in a `Next`, `NextCursor` or `NextBatch` state.
    *
    * To be used for optimizations, but keep in mind it's unsafe, as
    * its application isn't referentially transparent.
    *
    * @param f is the function mapping elements from the source to iterants
    */
  final def unsafeFlatMap[B](f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantConcat.unsafeFlatMap(this)(f)(F)

  /** Explicit covariance operator.
    *
    * The [[Iterant]] type isn't covariant in type param `A`, because
    * covariance doesn't play well with a higher-kinded type like
    * `F[_]`.  So in case you have an `Iterant[F, A]`, but need an
    * `Iterant[F, B]`, knowing that `A extends B`, then you can do an
    * `upcast`.
    *
    * Example:
    * {{{
    *   val source: Iterant[Task, List[Int]] = ???
    *
    *   // This will trigger an error because of the invariance:
    *   val sequences: Iterant[Task, Seq[Int]] = source
    *
    *   // But this will work just fine:
    *   val sequences: Iterant[Task, Seq[Int]] = source.upcast[Seq[Int]]
    * }}}
    */
  final def upcast[B >: A]: Iterant[F, B] =
    this.asInstanceOf[Iterant[F, B]]

  /** Creates a new iterant that upon evaluation will select
    * the first `n` elements from the source and then stop,
    * in the order they are emitted by the source.
    *
    * @param n is the number of elements to take from this iterant
    *
    * @return a new iterant instance that on evaluation will emit
    *         only the first `n` elements of this iterant
    */
  final def take(n: Int)(implicit F: Sync[F]): Iterant[F, A] =
    IterantTake(self, n)

  /** Creates a new iterable that only emits the last `n` elements
    * emitted by the source.
    *
    * In case the source triggers an error, then the underlying buffer
    * gets dropped and the error gets emitted immediately.
    */
  final def takeLast(n: Int)(implicit F: Sync[F]): Iterant[F, A] =
    IterantTakeLast(self, n)

  /** Takes longest prefix of elements that satisfy the given predicate
    * and returns a new iterant that emits those elements.
    *
    * @param p is the function that tests each element, stopping
    *          the streaming on the first `false` result
    */
  final def takeWhile(p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] =
    IterantTakeWhile(self, p)(F)

  /** Drops the first element of the source iterant, emitting the rest. */
  final def tail(implicit F: Sync[F]): Iterant[F, A] =
    IterantTail(self)(F)

  /** Skips over [[Iterant.Suspend]] states, along with [[Iterant.NextCursor]]
    * and [[Iterant.NextBatch]] states that signal empty collections.
    *
    * @see [[skipSuspendL]] $lazyVersionDesc
    */
  final def skipSuspendS(implicit F: Sync[F], C: Comonad[F]): Iterant[F, A] =
    C.extract(skipSuspendL(F))

  /** Skips over [[Iterant.Suspend]] states, along with [[Iterant.NextCursor]]
    * and [[Iterant.NextBatch]] states that signal empty collections.
    *
    * @see [[skipSuspendS]] $strictVersionDesc
    */
  final def skipSuspendL(implicit F: Sync[F]): F[Iterant[F, A]] =
    IterantSkipSuspend(self)

  /** Aggregates all elements in a `List` and preserves order.
    *
    * @see [[toListL]] $lazyVersionDesc
    */
  final def toListS(implicit F: Sync[F], C: Comonad[F]): List[A] =
    C.extract(IterantFoldLeftL.toListL(self)(F))

  /** Aggregates all elements in a `List` and preserves order.
    *
    * @see [[toListS]] $strictVersionDesc
    */
  final def toListL(implicit F: Sync[F]): F[List[A]] =
    IterantFoldLeftL.toListL(self)(F)

  /** Lazily zip two iterants together, using the given function `f` to
    * produce output values.
    *
    * The length of the result will be the shorter of the two
    * arguments.
    */
  final def zipMap[B, C](rhs: Iterant[F, B])(f: (A, B) => C)
    (implicit F: Sync[F]): Iterant[F, C] =
    IterantZipMap(this, rhs)(f)

  /** Lazily zip two iterants together.
    *
    * The length of the result will be the shorter of the two
    * arguments.
    */
  final def zip[B](rhs: Iterant[F, B])(implicit F: Sync[F]): Iterant[F, (A, B)] =
    (self zipMap rhs)((a, b) => (a, b))
}

/** Defines the standard [[Iterant]] builders. */
object Iterant extends IterantInstances with SharedDocs {
  /** Returns an [[IterantBuilders]] instance for the specified `F`
    * monadic type that can be used to build [[Iterant]] instances.
    *
    * Example:
    * {{{
    *   Iterant[Task].range(0, 10)
    * }}}
    */
  def apply[F[_]](implicit F: IterantBuilders.From[F]): F.Builders = F.instance

  /** Alias for [[now]]. */
  def pure[F[_], A](a: A): Iterant[F, A] =
    now[F, A](a)

  /** $builderNow */
  def now[F[_], A](a: A): Iterant[F, A] =
    lastS(a)

  /** $lastSDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[F[_], A](item: A): Iterant[F, A] =
    Last(item)

  /** $builderEval */
  def eval[F[_], A](a: => A)(implicit F: Sync[F]): Iterant[F, A] =
    Suspend(F.delay(nextS[F, A](a, F.pure(Halt(None)), F.unit)), F.unit)

  /** $nextSDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[F[_], A](item: A, rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Next[F, A](item, rest, stop)

  /** $nextCursorSDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextCursorS[F[_], A](items: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    NextCursor[F, A](items, rest, stop)

  /** $nextBatchSDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextBatchS[F[_], A](items: Batch[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    NextBatch[F, A](items, rest, stop)

  /** $haltSDesc
    *
    * @param ex $exParamDesc
    */
  def haltS[F[_], A](ex: Option[Throwable]): Iterant[F, A] =
    Halt[F, A](ex)

  /** Alias for [[Iterant.suspend[F[_],A](fa* suspend]].
    *
    * $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def defer[F[_] : Sync, A](fa: => Iterant[F, A]): Iterant[F, A] =
    suspend(fa)

  /** $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def suspend[F[_], A](fa: => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    suspend[F, A](F.delay(fa))

  /** $builderSuspendByF
    *
    * @param rest $restParamDesc
    */
  def suspend[F[_], A](rest: F[Iterant[F, A]])(implicit F: Applicative[F]): Iterant[F, A] =
    suspendS[F, A](rest, F.unit)

  /** $suspendSDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[F[_], A](rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Suspend[F, A](rest, stop)

  /** $builderRaiseError */
  def raiseError[F[_], A](ex: Throwable): Iterant[F, A] =
    Halt[F, A](Some(ex))

  /** $builderTailRecM */
  def tailRecM[F[_], A, B](a: A)(f: A => Iterant[F, Either[A, B]])(implicit F: Sync[F]): Iterant[F, B] = {
    f(a).flatMap {
      case Right(b) =>
        Iterant.now[F, B](b)
      case Left(nextA) =>
        suspend(tailRecM(nextA)(f))
    }
  }

  /** $builderFromArray */
  def fromArray[F[_], A : ClassTag](xs: Array[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextBatch(Batch.fromArray(xs), F.pure(empty[F, A]), F.unit)

  /** $builderFromSeq */
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    xs match {
      case ref: LinearSeq[_] =>
        fromList[F, A](ref.asInstanceOf[LinearSeq[A]])(F)
      case ref: IndexedSeq[_] =>
        fromIndexedSeq[F, A](ref.asInstanceOf[IndexedSeq[A]])(F)
      case _ =>
        fromIterable(xs)(F)
    }

  /** $builderFromList */
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextBatch(Batch.fromSeq(xs), F.pure(empty[F, A]), F.unit)

  /** $builderFromIndexedSeq */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextBatch(Batch.fromIndexedSeq(xs), F.pure(empty[F, A]), F.unit)

  /** $builderFromIterable */
  def fromIterable[F[_], A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F, A] = {
    val bs = if (xs.hasDefiniteSize) batches.defaultBatchSize else 1
    NextBatch(Batch.fromIterable(xs, bs), F.pure(empty[F, A]), F.unit)
  }

  /** $builderFromIterator */
  def fromIterator[F[_], A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F, A] = {
    val bs = if (xs.hasDefiniteSize) batches.defaultBatchSize else 1
    NextCursor[F, A](BatchCursor.fromIterator(xs, bs), F.pure(empty), F.unit)
  }

  /** $builderRange
    *
    * @param from $rangeFromParam
    * @param until $rangeUntilParam
    * @param step $rangeStepParam
    * @return $rangeReturnDesc
    */
  def range[F[_]](from: Int, until: Int, step: Int = 1)(implicit F: Applicative[F]): Iterant[F, Int] =
    NextBatch(Batch.range(from, until, step), F.pure(empty[F, Int]), F.unit)

  /** $builderEmpty */
  def empty[F[_], A]: Iterant[F, A] =
    Halt[F, A](None)

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
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      stop
  }

  /** $LastDesc
    *
    * @param item $lastParamDesc
    */
  final case class Last[F[_], A](item: A)
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      F.unit
  }

  /** $NextCursorDesc
    *
    * @param cursor $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextCursor[F[_], A](
    cursor: BatchCursor[A],
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      stop
  }

  /** $NextBatchDesc
    *
    * @param batch $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextBatch[F[_], A](
    batch: Batch[A],
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      stop
  }

  /** $SuspendDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Suspend[F[_], A](
    rest: F[Iterant[F, A]],
    stop: F[Unit])
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      stop
  }

  /** $HaltDesc
    *
    * @param ex $exParamDesc
    */
  final case class Halt[F[_], A](ex: Option[Throwable])
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      F.unit
  }
}

private[tail] trait IterantInstances extends IterantInstances1 {
  /** Provides type-class instances for `Iterant[Task, +A]`, based
    * on the default instances provided by
    * [[monix.eval.Task.catsAsync Task.catsAsync]].
    */
  implicit def iterantTaskInstances(implicit F: CatsAsyncInstances[Task]): IterantTaskInstances = {
    import CatsAsyncInstances.{ForParallelTask, ForTask}
    // Avoiding the creation of junk, because it is expensive
    F match {
      case ForTask => defaultIterantTaskRef
      case ForParallelTask => nondetIterantTaskRef
      case _ => new IterantTaskInstances()(F)
    }
  }

  /** Reusable instance for `Iterant[Task, A]`, avoids creating junk. */
  private[this] final val defaultIterantTaskRef =
    new IterantTaskInstances()(CatsAsyncInstances.ForTask)

  /** Provides type-class instances for `Iterant[Coeval, +A]`, based on
    * the default instances provided by
    * [[monix.eval.Coeval.catsSync Coeval.catsSync]].
    */
  implicit def iterantCoevalInstances(implicit F: CatsSyncInstances[Coeval]): IterantCoevalInstances = {
    import CatsSyncInstances.ForCoeval
    // Avoiding the creation of junk, because it is expensive
    F match {
      case `ForCoeval` => defaultIterantCoevalRef
      case _ => new IterantCoevalInstances()(F)
    }
  }

  /** Reusable instance for `Iterant[Coeval, A]`, avoids creating junk. */
  private[this] final val defaultIterantCoevalRef =
    new IterantCoevalInstances()(CatsSyncInstances.ForCoeval)
  /** Reusable instance for `Iterant[Task, A]`, avoids creating junk. */
  private[this] val nondetIterantTaskRef =
    new IterantTaskInstances()(CatsAsyncInstances.ForParallelTask)

  /** Provides type-class instances for `Iterant[Task, +A]`, based
    * on the default instances provided by
    * [[monix.eval.Task.catsAsync Task.catsAsync]].
    */
  class IterantTaskInstances(implicit F: CatsAsyncInstances[Task])
    extends MonadInstance[Task]()(F)

  /** Provides type-class instances for `Iterant[Coeval, +A]`, based on
    * the default instances provided by
    * [[monix.eval.Coeval.catsSync Coeval.catsSync]].
    */
  class IterantCoevalInstances(implicit F: CatsSyncInstances[Coeval])
    extends MonadInstance[Coeval]()(F)

}

private[tail] trait IterantInstances1 {
  /** Provides a `cats.effect.Sync` instance for [[Iterant]]. */
  implicit def monadInstance[F[_] : Sync]: MonadInstance[F] =
    new MonadInstance[F]()

  /** Provides a `cats.effect.Sync` instance for [[Iterant]]. */
  class MonadInstance[F[_]](implicit F: Sync[F])
    extends Monad[({type λ[α] = Iterant[F, α]})#λ]
    with MonoidK[({type λ[α] = Iterant[F, α]})#λ] {

    override def pure[A](a: A): Iterant[F, A] =
      Iterant.pure(a)

    override def map[A, B](fa: Iterant[F, A])(f: (A) => B): Iterant[F, B] =
      fa.map(f)(F)

    override def flatMap[A, B](fa: Iterant[F, A])(f: (A) => Iterant[F, B]): Iterant[F, B] =
      fa.flatMap(f)

    override def map2[A, B, Z](fa: Iterant[F, A], fb: Iterant[F, B])(f: (A, B) => Z): Iterant[F, Z] =
      fa.flatMap(a => fb.map(b => f(a, b))(F))

    override def ap[A, B](ff: Iterant[F, (A) => B])(fa: Iterant[F, A]): Iterant[F, B] =
      ff.flatMap(f => fa.map(a => f(a))(F))

    override def tailRecM[A, B](a: A)(f: (A) => Iterant[F, Either[A, B]]): Iterant[F, B] =
      Iterant.tailRecM(a)(f)(F)

    override def empty[A]: Iterant[F, A] =
      Iterant.empty

    override def combineK[A](x: Iterant[F, A], y: Iterant[F, A]): Iterant[F, A] =
      x.++(y)(F)
  }
}