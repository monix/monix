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

package monix.eval

import monix.eval.Iterant.{Next, Suspend}
import monix.eval.internal._
import monix.types.{Monad, MonadFilter, MonadRec, MonoidK}

import scala.collection.immutable.LinearSeq

/** The `Iterant` is a type that describes lazy, possibly asynchronous
  * streaming of elements, powered by [[Task]].
  *
  * It is similar somewhat in spirit to Scala's own
  * `collection.immutable.Stream` and with Java's `Iterable`, except
  * more flexible in what streaming it can describe due to evaluation being
  * controlled by the [[Task]] monadic type, which will control the evaluation.
  * In other words, this `Iterant` type is capable of strict or lazy,
  * synchronous or asynchronous evaluation.
  *
  * Consuming an `Iterant` happens typically in a loop where
  * the current step represents either a signal that the stream
  * is over, or a (head, rest) pair, very similar in spirit to
  * Scala's standard `List` or `Iterable`.
  *
  * The type is an ADT, meaning a composite of the following types:
  *
  *  - [[monix.eval.Iterant.Next Next]] which signals a single strict
  *    element, the `head` and a `rest` representing the rest of the stream
  *  - [[monix.eval.Iterant.NextSeq NextSeq]] is a variation on `Next`
  *    for signaling a whole strict batch of elements as a traversable
  *    [[scala.collection.Iterator Iterator]], along with the `rest`
  *    representing the rest of the stream
  *  - [[monix.eval.Iterant.NextGen NextGen]] is a variation on `Next`
  *    for signaling a whole batch of elements by means of an
  *    [[scala.collection.Iterable Iterable]], along with the `rest`
  *    representing the rest of the stream
  *  - [[monix.eval.Iterant.Suspend Suspend]] is for suspending the
  *    evaluation of a stream
  *  - [[monix.eval.Iterant.Halt Halt]] represents an empty
  *    stream, signaling the end, either in success or in error
  *  - [[monix.eval.Iterant.Last Last]] represents a one-element
  *    stream, where `Last(item)` as an optimisation on
  *    `Next(item, Task.now(Halt(None)), Task.unit)`
  *
  * ATTRIBUTION: this type was inspired by the `Streaming` type in the
  * Typelevel Cats library (later moved to Typelevel's Dogs), originally
  * committed in Cats by Erik Osheim. It was also inspired by other
  * push-based streaming abstractions, like the `Iteratee` or
  * `IAsyncEnumerable`.
  *
  * @tparam A is the type of the elements produced by this Iterant
  */
sealed abstract class Iterant[+A] {
  /** Builds a new iterant by applying a partial function to all elements of
    * the source on which the function is defined.
    *
    * @param pf the partial function that filters and maps the iterant
    * @tparam B the element type of the returned iterant.
    *
    * @return a new iterant resulting from applying the partial function
    *         `pf` to each element on which it is defined and collecting the results.
    *         The order of the elements is preserved.
    */
  final def collect[B](pf: PartialFunction[A,B]): Iterant[B] =
    IterantCollect(this, pf)

  /** Returns a computation that should be evaluated in
    * case the streaming must stop before reaching the end.
    *
    * This is useful to release any acquired resources,
    * like opened file handles or network sockets.
    */
  final def earlyStop: Task[Unit] =
    IterantStop.earlyStop(this)

  /** Given a routine make sure to execute it whenever
    * the consumer executes the current `stop` action.
    *
    * @param f is the function to execute on early stop
    */
  final def doOnEarlyStop(f: Task[Unit]): Iterant[A] =
    IterantStop.doOnEarlyStop(this, f)

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
    */
  def doOnFinish(f: Option[Throwable] => Task[Unit]): Iterant[A] =
    IterantStop.doOnFinish(this, f)

  /** Filters the iterant by the given predicate function,
    * returning only those elements that match.
    *
    * @param p the predicate used to test elements.
    *
    * @return a new iterant consisting of all elements that satisfy the given
    *         predicate. The order of the elements is preserved.
    */
  final def filter(p: A => Boolean): Iterant[A] =
    IterantFilter(this, p)

  /** Given a mapping function that returns a possibly lazy or asynchronous
    * result, applies it over the elements emitted by the stream.
    *
    * @param f is the mapping function that transforms the source
    */
  final def mapEval[B](f: A => Task[B]): Iterant[B] =
    IterantMapEval(this, f)

  /** Returns a new stream by mapping the supplied function
    * over the elements of the source.
    *
    * @param f is the mapping function that transforms the source
    */
  final def map[B](f: A => B): Iterant[B] =
    IterantMap(this, f)

  /** Applies the function to the elements of the source and concatenates the results.
    *
    * @param f is the function mapping elements from the source to iterants
    */
  final def flatMap[B](f: A => Iterant[B]): Iterant[B] =
    IterantConcat.flatMap(this, f)

  /** Applies the function to the elements of the source and concatenates the results.
    *
    * This variant of [[flatMap]] is not referentially transparent,
    * because it tries to apply function `f` immediately, in case
    * the `Iterant` is in a `Next`, `NextSeq` or `NextGen` state.
    *
    * To be used for optimizations, but keep in mind it's unsafe,
    * as its application isn't referentially transparent.
    *
    * @param f is the function mapping elements from the source to iterants
    */
  final def unsafeFlatMap[B](f: A => Iterant[B]): Iterant[B] =
    IterantConcat.unsafeFlatMap(this)(f)

  /** Given an `Iterant` that generates `Iterant` elements,
    * concatenates all the generated iterants.
    *
    * Equivalent with: `source.flatMap(x => x)`
    */
  final def flatten[B](implicit ev: A <:< Iterant[B]): Iterant[B] =
    flatMap(x => x)

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => Iterant[B]): Iterant[B] =
    flatMap(f)

  /** Alias for [[concat]]. */
  final def concat[B](implicit ev: A <:< Iterant[B]): Iterant[B] =
    flatten

  /** Appends the given stream to the end of the source,
    * effectively concatenating them.
    *
    * @param rhs is the iterant to append at the end of our source
    */
  final def ++[B >: A](rhs: Iterant[B]): Iterant[B] =
    IterantConcat.concat(this, rhs)

  /** Appends a stream given in the [[Task]] context, possibly lazy
    * evaluated, to the end of the source, effectively concatenating them.
    *
    * @param rhs is the iterant to append at the end of our source
    */
  final def ++[B >: A](rhs: Task[Iterant[B]]): Iterant[B] =
    IterantConcat.concat(this, Suspend(rhs, Task.unit))

  /** Prepends an element to the enumerator. */
  final def #::[B >: A](head: B): Iterant[B] =
    Next(head, Task.pure(this), earlyStop)

  /** Left associative fold using the function `f`.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    *
    * @param seed is the start value
    * @param op is the binary operator
    *
    * @return the result of inserting `op` between consecutive elements
    *         of this iterant, going from left to right with the
    *         `seed` as the start value, or `seed` if the iterant
    *         is empty.
    */
  final def foldLeftL[S](seed: => S)(op: (S,A) => S): Task[S] =
    IterantFoldLeft(this, seed)(op)

  /** Lazily fold the stream to a single value from the right.
    *
    * @see [[foldLeftL]] for a left associative fold
    */
  final def foldRightL[B](b: Task[B])(f: (A, Task[B]) => Task[B]): Task[B] =
    IterantFoldRight(this, b)(f)

  /** Aggregates all elements in a `List` and preserves order. */
  final def toListL[B >: A]: Task[List[B]] =
    IterantFoldLeft.toListL(this)
}

/** Defines the standard [[Iterant]] builders.
  *
  * @define NextDesc The [[monix.eval.Iterant.Next Next]] state
  *         of the [[Iterant]] represents a `head` / `rest`
  *         cons pair, where the `head` is a strict value.
  *
  *         Note the `head` being a strict value means that it is
  *         already known, whereas the `rest` is meant to be lazy and
  *         can have asynchronous behavior as well.
  *
  *         See [[monix.eval.Iterant.NextSeq NextSeq]]
  *         for a state where the head is a standard
  *         [[scala.collection.Iterator Iterator]] or
  *         [[monix.eval.Iterant.NextGen NextGen]] for a state where
  *         the head is a standard [[scala.collection.Iterable Iterable]].
  *
  * @define NextSeqDesc The [[monix.eval.Iterant.NextSeq NextSeq]] state
  *         of the [[Iterant]] represents an `items` / `rest` cons pair,
  *         where `items` is an [[scala.collection.Iterator Iterator]]
  *         type that can generate a whole batch of elements.
  *
  *         Useful for doing buffering, or by giving it an empty iterator,
  *         useful to postpone the evaluation of the next element.
  *
  * @define NextGenDesc The [[monix.eval.Iterant.NextGen NextGen]] state
  *         of the [[Iterant]] represents an `items` / `rest` cons pair,
  *         where `items` is an [[scala.collection.Iterable Iterable]]
  *         type that can generate a whole batch of elements.
  *
  * @define SuspendDesc The [[monix.eval.Iterant.Suspend Suspend]] state
  *         of the [[Iterant]] represents a suspended stream to be
  *         evaluated in the [[Task]] context. It is useful to delay the
  *         evaluation of a stream by deferring to [[Task]].
  *
  * @define LastDesc The [[monix.eval.Iterant.Last Last]] state of the
  *         [[Iterant]] represents a completion state as an alternative to
  *         [[monix.eval.Iterant.Halt Halt(None)]], describing one
  *         last element.
  *
  *         It is introduced as an optimization, being equivalent to
  *         `Next(item, Task.now(Halt(None)), Task.unit)`, to avoid extra processing
  *         in the monadic [[Task]] context and to short-circuit operations
  *         such as concatenation and `flatMap`.
  *
  * @define HaltDesc The [[monix.eval.Iterant.Halt Halt]] state
  *         of the [[Iterant]] represents the completion state
  *         of a stream, with an optional exception if an error
  *         happened.
  *
  *         `Halt` is received as a final state in the iteration process.
  *         This state cannot be followed by any other element and
  *         represents the end of the stream.
  *
  *         @see [[Iterant.Last]] for an alternative that signals one
  *              last item, as an optimisation
  *
  * @define headParamDesc is the current element to be signaled
  *
  * @define lastParamDesc is the last element being signaled, after which
  *         the consumer can stop the iteration
  *
  * @define cursorParamDesc is an [[scala.collection.Iterator Iterator]] type
  *         that can generate elements by traversing a collection, a standard
  *         array or any `Iterator`
  *
  * @define generatorParamDesc is a [[scala.collection.Iterable Iterable]]
  *         type that can generate elements by traversing a collection,
  *         a standard array or any `Iterable`
  *
  * @define restParamDesc is the next state in the sequence that
  *         will produce the rest of the stream when evaluated
  *
  * @define stopParamDesc is a computation to be executed in case
  *         streaming is stopped prematurely, giving it a chance
  *         to do resource cleanup (e.g. close file handles)
  *
  * @define exParamDesc is an error to signal at the end of the stream,
  *        or `None` in case the stream has completed normally
  *
  * @define suspendByNameParam is the by-name parameter that will generate
  *         the stream when evaluated
  */
object Iterant {
  /** Lifts a strict value into the stream context,
    * returning a stream of one element.
    */
  def now[A](a: A): Iterant[A] =
    lastS(a)

  /** Alias for [[now]]. */
  def pure[A](a: A): Iterant[A] =
    now(a)

  /** Lifts a non-strict value into the stream context,
    * returning a stream of one element that is lazily evaluated.
    */
  def eval[A](a: => A): Iterant[A] =
    Suspend(Task.eval(nextS(a, emptyReferenceT, Task.unit)), Task.unit)

  /** Builds a stream state equivalent with [[Iterant.Next]].
    *
    * $NextDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[A](item: A, rest: Task[Iterant[A]], stop: Task[Unit]): Iterant[A] =
    Next(item, rest, stop)

  /** Builds a stream state equivalent with [[Iterant.NextSeq]].
    *
    * $NextSeqDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextSeqS[A](items: Iterator[A], rest: Task[Iterant[A]], stop: Task[Unit]): Iterant[A] =
    NextSeq(items, rest, stop)

  /** Builds a stream state equivalent with [[Iterant.NextGen]].
    *
    * $NextGenDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextGenS[A](items: Iterable[A], rest: Task[Iterant[A]], stop: Task[Unit]): Iterant[A] =
    NextGen(items, rest, stop)

  /** Builds a stream state equivalent with [[Iterant.NextSeq]].
    *
    * $SuspendDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[A](rest: Task[Iterant[A]], stop: Task[Unit]): Iterant[A] =
    Suspend(rest, stop)

  /** Builds a stream state equivalent with [[Iterant.Last]].
    *
    * $LastDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[A](item: A): Iterant[A] =
    Last(item)

  /** Builds a stream state equivalent with [[Iterant.Halt]].
    *
    * $HaltDesc
    *
    * @param ex $exParamDesc
    */
  def haltS[A](ex: Option[Throwable]): Iterant[A] =
    if (ex eq None) emptyReference else Halt(ex)

  /** Promote a non-strict value representing a stream to a
    * stream of the same type, effectively delaying
    * its initialisation.
    *
    * @param fa $suspendByNameParam
    */
  def suspend[A](fa: => Iterant[A]): Iterant[A] =
    suspend(Task.eval(fa))

  /** Defers the stream generation to the underlying [[Task]] evaluation
    * context, building a reference equivalent with [[Iterant.Suspend]].
    *
    * $SuspendDesc
    *
    * @param rest $restParamDesc
    */
  def suspend[A](rest: Task[Iterant[A]]): Iterant[A] =
    suspendS(rest, Task.unit)

  /** Alias for [[Iterant.suspend[A](fa* suspend]].
    *
    * @param fa $suspendByNameParam
    */
  def defer[A](fa: => Iterant[A]): Iterant[A] =
    suspend(fa)
  
  /** Returns an empty stream. */
  def empty[A]: Iterant[A] =
    emptyReference

  /** Returns an empty stream that ends with an error. */
  def raiseError[A](ex: Throwable): Iterant[A] =
    Halt(Some(ex))

  /** Keeps calling `f` and concatenating the resulting iterants
    * for each `scala.util.Left` event emitted by the source, concatenating
    * the resulting iterants and generating `scala.util.Right[B]` items.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    */
  def tailRecM[A, B](a: A)(f: A => Iterant[Either[A, B]]): Iterant[B] =
    IterantConcat.tailRecM(a)(f)

  /** Converts any [[Task]] to an [[Iterant]] producing one value. */
  def fromTask[A](fa: Task[A]): Iterant[A] =
    Suspend(fa.map(Last.apply), Task.unit)

  /** Converts any standard `Array` into a stream. */
  def fromArray[A](xs: Array[A]): Iterant[A] =
    NextGen(xs, emptyReferenceT, Task.unit)

  /** Converts any Scala `collection.immutable.LinearSeq`
    * into a stream.
    */
  def fromList[A](xs: LinearSeq[A]): Iterant[A] =
    NextGen(xs, emptyReferenceT, Task.unit)

  /** Converts any Scala [[scala.collection.IndexedSeq]]
    * (e.g. `Vector`) into a stream.
    */
  def fromIndexedSeq[A](xs: IndexedSeq[A]): Iterant[A] =
    NextGen(xs, emptyReferenceT, Task.unit)

  /** Converts any [[scala.collection.Seq]] into a stream. */
  def fromSeq[A](xs: Seq[A]): Iterant[A] =
    NextGen(xs, emptyReferenceT, Task.unit)

  /** Converts a [[scala.collection.Iterable]] into a stream. */
  def fromIterable[A](xs: Iterable[A]): Iterant[A] =
    NextGen(xs, emptyReferenceT, Task.unit)

  /** Converts a [[scala.collection.Iterator]] into a stream. */
  def fromIterator[A](xs: Iterator[A]): Iterant[A] =
    NextSeq[A](xs, Task.now(empty), Task.unit)

  /** Builds a stream that on evaluation will produce equally
    * spaced values in some integer interval.
    *
    * @param from the start value of the stream
    * @param until the end value of the stream (exclusive from the stream)
    * @param step the increment value of the tail (must be positive or negative)
    * @return the tail producing values `from, from + step, ...` up to, but excluding `until`
    */
  def range(from: Int, until: Int, step: Int = 1): Iterant[Int] =
    NextGen(Iterable.range(from, until, step), emptyReferenceT, Task.unit)
  
  /** $NextDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Next[+A](
    item: A,
    rest: Task[Iterant[A]],
    stop: Task[Unit])
    extends Iterant[A]

  /** $LastDesc
    *
    * @param item $lastParamDesc
    */
  final case class Last[+A](item: A)
    extends Iterant[A]

  /** $NextSeqDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextSeq[+A](
    items: Iterator[A],
    rest: Task[Iterant[A]],
    stop: Task[Unit])
    extends Iterant[A]

  /** $NextGenDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class NextGen[+A](
    items: Iterable[A],
    rest: Task[Iterant[A]],
    stop: Task[Unit])
    extends Iterant[A]

  /** $SuspendDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  final case class Suspend[+A](
    rest: Task[Iterant[A]],
    stop: Task[Unit])
    extends Iterant[A]

  /** $HaltDesc
    *
    * @param ex $exParamDesc
    */
  final case class Halt(ex: Option[Throwable])
    extends Iterant[Nothing]

  private[this] final val emptyReference: Iterant[Nothing] =
    Halt(None)
  private[this] final val emptyReferenceT: Task[Iterant[Nothing]] =
    Task.now(emptyReference)

  /** Type-class instances for [[Iterant]]. */
  implicit val typeClassInstances: TypeClassInstances = new TypeClassInstances

  /** Groups the implementation for the type-classes defined in [[monix.types]]. */
  class TypeClassInstances extends Monad.Instance[Iterant]
    with MonadRec.Instance[Iterant]
    with MonadFilter.Instance[Iterant]
    with MonoidK.Instance[Iterant] {

    override def empty[A]: Iterant[A] =
      Iterant.empty
    override def combineK[A](x: Iterant[A], y: Iterant[A]): Iterant[A] =
      x ++ y
    override def tailRecM[A, B](a: A)(f: (A) => Iterant[Either[A, B]]): Iterant[B] =
      Iterant.tailRecM(a)(f)
    override def pure[A](a: A): Iterant[A] =
      Iterant.now(a)
    override def map2[A, B, Z](fa: Iterant[A], fb: Iterant[B])(f: (A, B) => Z): Iterant[Z] =
      for (a <- fa; b <- fb) yield f(a,b)
    override def ap[A, B](ff: Iterant[(A) => B])(fa: Iterant[A]): Iterant[B] =
      ff.flatMap(f => fa.map(a => f(a)))
    override def eval[A](a: => A): Iterant[A] =
      Iterant.eval(a)
    override def map[A, B](fa: Iterant[A])(f: (A) => B): Iterant[B] =
      fa.map(f)
    override def flatMap[A, B](fa: Iterant[A])(f: (A) => Iterant[B]): Iterant[B] =
      fa.flatMap(f)
    override def suspend[A](fa: => Iterant[A]): Iterant[A] =
      Iterant.defer(fa)
    override def filter[A](fa: Iterant[A])(f: (A) => Boolean): Iterant[A] =
      fa.filter(f)
    override def flatten[A](ffa: Iterant[Iterant[A]]): Iterant[A] =
      ffa.flatten
  }
}

