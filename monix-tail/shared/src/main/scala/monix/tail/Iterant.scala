/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import java.io.PrintStream

import cats.arrow.FunctionK
import cats.effect.{Async, Effect, Sync}
import cats.{Applicative, CoflatMap, Eq, MonadError, Monoid, MonoidK, Order, Parallel}
import monix.eval.instances.{CatsAsyncForTask, CatsBaseForTask, CatsSyncForCoeval}
import monix.eval.{Coeval, Task}
import monix.execution.Scheduler
import monix.tail.batches.{Batch, BatchCursor}
import monix.tail.internal._
import org.reactivestreams.Publisher

import scala.collection.immutable.LinearSeq
import scala.reflect.ClassTag

/** The `Iterant` is a type that describes lazy, possibly asynchronous
  * streaming of elements using a pull-based protocol.
  *
  * It is similar somewhat in spirit to Scala's own
  * `collection.immutable.Stream` and with Java's `Iterable`, except
  * that it is more composable and more flexible due to evaluation being
  * controlled by an `F[_]` monadic type that you have to supply
  * (like [[monix.eval.Task Task]], [[monix.eval.Coeval Coeval]] or
  * `cats.effect.IO`) which will control the evaluation. In other words,
  * this `Iterant` type is capable of strict or lazy, synchronous or
  * asynchronous evaluation.
  *
  * Consumption of an `Iterant` happens typically in a loop where
  * the current step represents either a signal that the stream
  * is over, or a (head, rest) pair, very similar in spirit to
  * Scala's standard `List` or `Iterable`.
  *
  * The type is an ADT, meaning a composite of the following types:
  *
  *  - [[monix.tail.Iterant.Next Next]] which signals a single strict
  *    element, the `head` and a `rest` representing the rest of the stream
  *
  *  - [[monix.tail.Iterant.NextBatch NextBatch]] is a variation on `Next`
  *    for signaling a whole batch of elements by means of a
  *    [[monix.tail.batches.Batch Batch]], a type that's similar with
  *    Scala's `Iterable`, along with the `rest` of the stream.
  *
  *  - [[monix.tail.Iterant.NextCursor NextCursor]] is a variation on `Next`
  *    for signaling a whole strict batch of elements as a traversable
  *    [[monix.tail.batches.BatchCursor BatchCursor]], a type that's similar
  *    with Scala's `Iterator`, along with the `rest` of the stream.
  *
  *  - [[monix.tail.Iterant.Suspend Suspend]] is for suspending the
  *    evaluation of a stream.
  *
  *  - [[monix.tail.Iterant.Halt Halt]] represents an empty stream,
  *    signaling the end, either in success or in error.
  *
  *  - [[monix.tail.Iterant.Last Last]] represents a one-element
  *    stream, where `Last(item)` as an optimisation on
  *    `Next(item, F.pure(Halt(None)), F.unit)`.
  *
  * ==Parametric Polymorphism==
  *
  * The `Iterant` type accepts as type parameter an `F` monadic type
  * that is used to control how evaluation happens. For example you can
  * use [[monix.eval.Task Task]], in which case the streaming can have
  * asynchronous behavior, or you can use [[monix.eval.Coeval Coeval]]
  * in which case it can behave like a normal, synchronous `Iterable`.
  *
  * As restriction, this `F[_]` type used should be stack safe in
  * `map` and `flatMap`, otherwise you might get stack-overflow
  * exceptions. This is why in general the type class required
  * for `F` is `cats.effect.Sync`.
  *
  * When building instances, type `F[_]` which handles the evaluation
  * needs to be specified upfront. Example:
  *
  * {{{
  *   import cats.effect.IO
  *   import monix.eval.{Task, Coeval}
  *
  *   // Builds an Iterant powered by Monix's Task
  *   Iterant[Task].of(1, 2, 3)
  *
  *   // Builds an Iterant powered by Monix's Coeval
  *   Iterant[Coeval].of(1, 2, 3)
  *
  *   // Builds an Iterant powered by Cats's IO
  *   Iterant[IO].of(1, 2, 3)
  * }}}
  *
  * You'll usually pick between `Task`, `Coeval` or `IO` for your
  * needs.
  *
  * ==Attribution==
  *
  * This type was inspired by the `Streaming` type in the
  * [[https://typelevel.org/cats/ Typelevel Cats]] library (later moved
  * to [[https://github.com/stew/dogs Dogs]]), originally committed in
  * Cats by Erik Osheim. It was also inspired by other push-based
  * streaming abstractions, like the `Iteratee` or `IAsyncEnumerable`.
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

  /** Appends a stream to the end of the source, effectively
    * concatenating them.
    *
    * The right hand side is suspended in the `F[_]` data type, thus
    * allowing for laziness.
    *
    * Example: {{{
    *   // Yields 1, 2, 3, 4
    *   Iterant[Task].of(1, 2) ++ Task.suspend {
    *     Iterant[Task].of(3, 4)
    *   }
    * }}}
    *
    * @param rhs is the iterant to append at the end of our source.
    */
  final def ++[B >: A](rhs: F[Iterant[F, B]])(implicit F: Applicative[F]): Iterant[F, B] =
    IterantConcat.concat(self.upcast[B], Suspend(rhs, F.unit))

  /** Prepends an element to the iterant, returning a new
    * iterant that will start with the given `head` and then
    * continue with the source.
    *
    * Example: {{{
    *   // Yields 1, 2, 3, 4
    *   1 +: Iterant[Task].of(2, 3, 4)
    * }}}
    *
    * @param head is the element to prepend at the start of
    *        this iterant
    */
  final def +:[B >: A](head: B)(implicit F: Applicative[F]): Iterant[F, B] =
    Next(head, F.pure(self.upcast[B]), earlyStop)

  /** Appends the right hand side element to the end of this iterant.
    *
    * Example: {{{
    *   // Yields 1, 2, 3, 4
    *   Iterant[Task].of(1, 2, 3) :+ 4
    * }}}
    *
    * @param elem is the element to append at the end
    */
  final def :+[B >: A](elem: B)(implicit F: Applicative[F]): Iterant[F, B] =
    ++(Next[F, B](elem, F.pure(Halt[F, B](None)), F.unit))(F)

  /** Appends the given stream to the end of the source, effectively
    * concatenating them.
    *
    * Example: {{{
    *   // Yields 1, 2, 3, 4
    *   Iterant[Task].of(1, 2) ++ Iterant[Task].of(3, 4)
    * }}}
    *
    * @param rhs is the (right hand side) iterant to concatenate at
    *        the end of this iterant.
    */
  final def ++[B >: A](rhs: Iterant[F, B])(implicit F: Applicative[F]): Iterant[F, B] =
    IterantConcat.concat(this.upcast[B], rhs)(F)

  /** Explicit covariance operator.
    *
    * The [[Iterant]] type isn't covariant in type param `A`, because
    * covariance doesn't play well with a higher-kinded type like
    * `F[_]`.  So in case you have an `Iterant[F, A]`, but need an
    * `Iterant[F, B]`, knowing that `A extends B`, then you can do an
    * `upcast`.
    *
    * Example: {{{
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

  /** Converts the source `Iterant` that emits `A` elements into an
    * iterant that emits `Either[Throwable, A]`, thus materializing
    * whatever error that might interrupt the stream.
    *
    * Example: {{{
    *   // Yields Right(1), Right(2), Right(3)
    *   Iterant[Task].of(1, 2, 3).attempt
    *
    *
    *   // Yields Right(1), Right(2), Left(DummyException())
    *   (Iterant[Task].of(1, 2) ++
    *     Iterant[Task].raiseError(DummyException())).attempt
    * }}}
    */
  final def attempt(implicit F: Sync[F]): Iterant[F, Either[Throwable, A]] =
    IterantOnError.attempt(self)

  /** Optimizes the access to the source by periodically gathering
    * items emitted into batches of the specified size and emitting
    * [[monix.tail.Iterant.NextBatch NextBatch]] nodes.
    *
    * For this operation we have this law:
    *
    * {{{
    *   source.batched(16) <-> source
    * }}}
    *
    * This means that the result will emit exactly what the source
    * emits, however the underlying representation will be different,
    * the emitted notes being of type `NextBatch`, wrapping arrays
    * with the length equal to the given `count`.
    *
    * Very similar in behavior with [[bufferTumbling]], however the
    * batches are implicit, not explicit. Useful for optimization.
    */
  def batched(count: Int)(implicit F: Sync[F]): Iterant[F, A] =
    IterantBuffer.batched(self, count)(F)

  /** Periodically gather items emitted by an iterant into bundles
    * and emit these bundles rather than emitting the items one at a
    * time. This version of `buffer` is emitting items once the
    * internal buffer has reached the given count.
    *
    * If the source iterant completes, then the current buffer gets
    * signaled downstream. If the source triggers an error then the
    * current buffer is being dropped and the error gets propagated
    * immediately.
    *
    * {{{
    *   // Yields Seq(1, 2, 3), Seq(4, 5, 6), Seq(7)
    *   Iterant[Coeval].of(1, 2, 3, 4, 5, 6, 7).bufferTumbling(3)
    * }}}
    *
    * @see [[bufferSliding]] for the more flexible version that allows
    *      to specify a `skip` argument.
    *
    * @param count the maximum size of each buffer before it should
    *        be emitted
    */
  def bufferTumbling(count: Int)(implicit F: Sync[F]): Iterant[F, Seq[A]] =
    bufferSliding(count, count)

  /** Returns an iterant that emits buffers of items it collects from
    * the source iterant. The resulting iterant emits buffers
    * every `skip` items, each containing `count` items.
    *
    * If the source iterant completes, then the current buffer gets
    * signaled downstream. If the source triggers an error then the
    * current buffer is being dropped and the error gets propagated
    * immediately.
    *
    * For `count` and `skip` there are 3 possibilities:
    *
    *  1. in case `skip == count`, then there are no items dropped and
    *     no overlap, the call being equivalent to `buffer(count)`
    *  1. in case `skip < count`, then overlap between buffers
    *     happens, with the number of elements being repeated being
    *     `count - skip`
    *  1. in case `skip > count`, then `skip - count` elements start
    *     getting dropped between windows
    *
    * Example:
    *
    * {{{
    *   val source = Iterant[Coeval].of(1, 2, 3, 4, 5, 6, 7)
    *
    *   // Yields Seq(1, 2, 3), Seq(4, 5, 6), Seq(7)
    *   source.bufferSliding(3, 3)
    *
    *   // Yields Seq(1, 2, 3), Seq(5, 6, 7)
    *   source.bufferSliding(3, 4)
    *
    *   // Yields Seq(1, 2, 3), Seq(3, 4, 5), Seq(5, 6, 7)
    *   source.bufferSliding(3, 2)
    * }}}
    *
    * @param count the maximum size of each buffer before it should
    *        be emitted
    *
    * @param skip how many items emitted by the source iterant should
    *        be skipped before starting a new buffer. Note that when
    *        skip and count are equal, this is the same operation as
    *        `bufferTumbling(count)`
    */
  final def bufferSliding(count: Int, skip: Int)(implicit F: Sync[F]): Iterant[F, Seq[A]] =
    IterantBuffer.sliding(self, count, skip)

  /** Builds a new iterant by applying a partial function to all
    * elements of the source on which the function is defined.
    *
    * Example: {{{
    *   // Yields 2, 4, 6
    *   Iterant[Task].of(1, 2, 3, 4, 5, 6)
    *     .map { x => Option(x).filter(_ % 2 == 0) }
    *     .collect { case Some(x) => x }
    * }}}
    *
    * @param pf the partial function that filters and maps the iterant
    * @tparam B the element type of the returned iterant.
    *
    * @return a new iterant resulting from applying the partial
    *         function `pf` to each element on which it is defined and
    *         collecting the results. The order of the elements is
    *         preserved.
    */
  final def collect[B](pf: PartialFunction[A, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantCollect(this, pf)(F)

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

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    flatMap(f)

  /** Applies the function to the elements of the source and
    * concatenates the results.
    *
    * This operation is the monadic "bind", with all laws it entails.
    *
    * Also note that the implementation can use constant memory
    * depending on usage, thus it can be used in tail recursive loops.
    *
    * Example: {{{
    *   // Effectively equivalent with .filter
    *   Iterant[Task].of(1, 2, 3, 4, 5, 6).flatMap { elem =>
    *     if (elem % 2 == 0)
    *       Iterant[Task].pure(elem)
    *     else
    *       Iterant[Task].empty
    *   }
    * }}}
    *
    * @param f is the function mapping elements from the
    *        source to iterants
    */
  final def flatMap[B](f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantConcat.flatMap(this, f)(F)

  /** Counts the total number of elements emitted by the source.
    *
    * Example:
    *
    * {{{
    *   // Yields 100
    *   Iterant[IO].range(0, 100).countL
    *
    *   // Yields 1
    *   Iterant[IO].pure(1).countL
    *
    *   // Yields 0
    *   Iterant[IO].empty[Int].countL
    * }}}
    */
  final def countL(implicit F: Sync[F]): F[Long] =
    foldLeftL(0L)((c, _) => c + 1)

  /** Suppress duplicate consecutive items emitted by the source.
    *
    * Example:
    * {{{
    *   // Yields 1, 2, 1, 3, 2, 4
    *   Iterant[Coeval].of(1, 1, 1, 2, 2, 1, 1, 3, 3, 3, 2, 2, 4, 4, 4)
    *     .distinctUntilChanged
    * }}}
    *
    * Duplication is detected by using the equality relationship
    * provided by the `cats.Eq` type class. This allows one to
    * override the equality operation being used (e.g. maybe the
    * default `.equals` is badly defined, or maybe you want reference
    * equality, so depending on use case).
    *
    * In case type `A` is a primitive type and an `Eq[A]` instance
    * is not in scope, then you probably need this import:
    * {{{
    *   import cats.instances.all._
    * }}}
    *
    * Or in case your type `A` does not have an `Eq[A]` instance
    * defined for it, then you can quickly define one like this:
    * {{{
    *   import cats.Eq
    *
    *   implicit val eqA = Eq.fromUniversalEquals[A]
    * }}}
    *
    * @param A is the `cats.Eq` instance that defines equality for `A`
    */
  final def distinctUntilChanged(implicit F: Sync[F], A: Eq[A]): Iterant[F, A] =
    distinctUntilChangedByKey(identity)(F, A)

  /** Given a function that returns a key for each element emitted by
    * the source, suppress consecutive duplicate items.
    *
    * Example:
    *
    * {{{
    *   // Yields 1, 2, 3, 4
    *   Iterant[Coeval].of(1, 3, 2, 4, 2, 3, 5, 7, 4)
    *     .distinctUntilChangedBy(_ % 2)
    * }}}
    *
    * Duplication is detected by using the equality relationship
    * provided by the `cats.Eq` type class. This allows one to
    * override the equality operation being used (e.g. maybe the
    * default `.equals` is badly defined, or maybe you want reference
    * equality, so depending on use case).
    *
    * In case type `K` is a primitive type and an `Eq[K]` instance
    * is not in scope, then you probably need this import:
    * {{{
    *   import cats.instances.all._
    * }}}
    *
    * Or in case your type `K` does not have an `Eq[K]` instance
    * defined for it, then you can quickly define one like this:
    * {{{
    *   import cats.Eq
    *
    *   implicit val eqK = Eq.fromUniversalEquals[K]
    * }}}
    *
    * @param key is a function that returns a `K` key for each element,
    *        a value that's then used to do the deduplication
    *
    * @param K is the `cats.Eq` instance that defines equality for
    *        the key type `K`
    */
  final def distinctUntilChangedByKey[K](key: A => K)(implicit F: Sync[F], K: Eq[K]): Iterant[F, A] =
    IterantDistinctUntilChanged(self, key)(F, K)

  /** Given a routine make sure to execute it whenever
    * the consumer executes the current `stop` action.
    *
    * Example: {{{
    *   iterant.doOnEarlyStop(Task.eval {
    *     println("Was stopped early!")
    *   })
    * }}}
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
    * Example: {{{
    *   iterant.doOnEarlyStop(err => Task.eval {
    *     err match {
    *       case Some(e) => log.error(e)
    *       case None =>
    *         println("Was consumed successfully!")
    *     }
    *   })
    * }}}
    *
    * @param f is the function to execute on early stop
    */
  final def doOnFinish(f: Option[Throwable] => F[Unit])(implicit F: Sync[F]): Iterant[F, A] =
    IterantStop.doOnFinish(this, f)(F)

  /** Drops the first `n` elements (from the start).
    *
    * Example: {{{
    *   // Yields 4, 5
    *   Iterant[Task].of(1, 2, 3, 4, 5).drop(3)
    * }}}
    *
    * @param n the number of elements to drop
    * @return a new iterant that drops the first ''n'' elements
    *         emitted by the source
    */
  final def drop(n: Int)(implicit F: Sync[F]): Iterant[F, A] =
    IterantDrop(self, n)(F)

  /** Drops the longest prefix of elements that satisfy the given
    * predicate and returns a new iterant that emits the rest.
    *
    * Example: {{{
    *   // Yields 4, 5
    *   Iterant[Task].of(1, 2, 3, 4, 5).dropWhile(_ < 4)
    * }}}
    *
    * @param p is the predicate used to test whether the current
    *        element should be dropped, if `true`, or to interrupt
    *        the dropping process, if `false`
    *
    * @return a new iterant that drops the elements of the source
    *         until the first time the given predicate returns `false`
    */
  final def dropWhile(p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] =
    IterantDropWhile(self, p)

  /** Dumps incoming events to standard output with provided prefix.
    *
    * Utility that can be used for debugging purposes.
    *
    * Example: {{{
    *   Iterant[Task].range(0, 4)
    *     .dump("O")
    *     .completeL.runAsync
    *
    *   // Results in:
    *
    *   0: O --> 0
    *   1: O --> 1
    *   2: O --> 2
    *   3: O --> 3
    *   4: O completed
    * }}}
    */
  final def dump(prefix: String, out: PrintStream = System.out)(implicit F: Sync[F]): Iterant[F, A] =
    IterantDump(this, prefix, out)

  /** Returns a computation that should be evaluated in case the
    * streaming must stop before reaching the end.
    *
    * This is useful to release any acquired resources, like opened
    * file handles or network sockets.
    */
  def earlyStop(implicit F: Applicative[F]): F[Unit]

  /** Returns `true` in case the given predicate is satisfied by any
    * of the emitted items, or `false` in case the end of the stream
    * has been reached with no items satisfying the given predicate.
    *
    * Example: {{{
    *   val source = Iterant[Coeval].of(1, 2, 3, 4)
    *
    *   // Yields true
    *   source.existsL(_ % 2 == 0)
    *
    *   // Yields false
    *   source.existsL(_ % 7 == 0)
    * }}}
    *
    * @param p is a predicate function that's going to test each item
    *        emitted by the source until we get a positive match for
    *        one of them or until the stream ends
    *
    * @return `true` if any of the items satisfies the given predicate
    *        or `false` if none of them do
    */
  final def existsL(p: A => Boolean)(implicit F: Sync[F]): F[Boolean] = {
    val next = Left(false)
    foldWhileLeftL(false)((_, e) => if (p(e)) Right(true) else next)
  }

  /** Left associative fold using the function `op` that can be
    * short-circuited.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state either until the end, or until `op` returns
    * a `Right` result, when the summary is returned.
    *
    * Example: {{{
    *   // Sums first 10 items
    *   Iterant[Task].range(0, 1000).foldWhileLeftL((0, 0)) {
    *     case ((sum, count), e) =>
    *       val next = (sum + e, count + 1)
    *       if (count + 1 < 10) Left(next) else Right(next)
    *   }
    *
    *   // Implements exists(predicate)
    *   Iterant[Task].of(1, 2, 3, 4, 5).foldWhileLeftL(false) {
    *     (default, e) =>
    *       if (e == 3) Right(true) else Left(default)
    *   }
    *
    *   // Implements forall(predicate)
    *   Iterant[Task].of(1, 2, 3, 4, 5).foldWhileLeftL(true) {
    *     (default, e) =>
    *       if (e != 3) Right(false) else Left(default)
    *   }
    * }}}
    *
    * @see [[Iterant.foldWhileLeftL]] for the lazy, potentially
    *      asynchronous version.
    *
    * @param seed is the start value
    * @param op is the binary operator returning either `Left`,
    *        signaling that the state should be evolved or a `Right`,
    *        signaling that the process can be short-circuited and
    *        the result returned immediately
    *
    * @return the result of inserting `op` between consecutive
    *         elements of this iterant, going from left to right with
    *         the `seed` as the start value, or `seed` if the iterant
    *         is empty
    */
  final def foldWhileLeftL[S](seed: => S)(op: (S, A) => Either[S, S])(implicit F: Sync[F]): F[S] =
    IterantFoldWhileLeft.strict(self, seed, op)

  /** Filters the iterant by the given predicate function, returning
    * only those elements that match.
    *
    * Example: {{{
    *   // Yields 2, 4, 6
    *   Iterant[Task].of(1, 2, 3, 4, 5, 6).filter(_ % 2 == 0)
    * }}}
    *
    * @param p the predicate used to test elements.
    *
    * @return a new iterant consisting of all elements that satisfy
    *         the given predicate. The order of the elements is
    *         preserved.
    */
  final def filter(p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] =
    IterantFilter(this, p)(F)

  /** Returns `true` in case the given predicate is satisfied by all
    * of the emitted items, or `false` in case the given predicate
    * fails for any of those items.
    *
    * Example: {{{
    *   val source = Iterant[Coeval].of(1, 2, 3, 4)
    *
    *   // Yields false
    *   source.forallL(_ % 2 == 0)
    *
    *   // Yields true
    *   source.existsL(_ < 10)
    * }}}
    *
    * @param p is a predicate function that's going to test each item
    *        emitted by the source until we get a negative match for
    *        one of them or until the stream ends
    *
    * @return `true` if all of the items satisfy the given predicate
    *        or `false` if any of them don't
    */
  final def forallL(p: A => Boolean)(implicit F: Sync[F]): F[Boolean] = {
    val next = Left(true)
    foldWhileLeftL(true)((_, e) => if (!p(e)) Right(false) else next)
  }

  /** Consumes the source iterable, executing the given callback for
    * each element.
    *
    * Example: {{{
    *   // Prints all elements, each one on a different line
    *   Iterant[Task].of(1, 2, 3).foreachL { elem =>
    *     println("Elem: " + elem.toString)
    *   }
    * }}}
    *
    * @param cb is the callback to call for each element emitted
    *        by the source.
    */
  final def foreach(cb: A => Unit)(implicit F: Sync[F]): F[Unit] =
    map(cb)(F).completeL

  /** Upon evaluation of the result, consumes this iterant to
    * completion.
    *
    * Example: {{{
    *   val onFinish: Task[Unit] =
    *     iterant.completeL >> Task.eval(println("Done!"))
    * }}}
    */
  final def completeL(implicit F: Sync[F]): F[Unit] =
    IterantCompleteL(this)(F)

  /** Returns a new stream by mapping the supplied function over the
    * elements of the source.
    *
    * {{{
    *   // Yields 2, 4, 6
    *   Iterant[Task].of(1, 2, 3).map(_ * 2)
    * }}}
    *
    * @param f is the mapping function that transforms the source
    *
    * @return a new iterant that's the result of mapping the given
    *         function over the source
    */
  final def map[B](f: A => B)(implicit F: Sync[F]): Iterant[F, B] =
    IterantMap(this, f)(F)

  /** Optionally selects the first element.
    *
    * {{{
    *   // Yields Some(1)
    *   Iterant[Task].of(1, 2, 3, 4).headOptionL
    *
    *   // Yields None
    *   Iterant[Task].empty[Int].headOptionL
    * }}}
    *
    * @return the first element of this iterant if it is nonempty, or
    *         `None` if it is empty, in the `F` context.
    */
  final def headOptionL(implicit F: Sync[F]): F[Option[A]] =
    IterantSlice.headOptionL(self)(F)

  /** Given a mapping function that returns a possibly lazy or
    * asynchronous result, applies it over the elements emitted by the
    * stream.
    *
    * {{{
    *   Iterant[Task].of(1, 2, 3, 4).mapEval { elem =>
    *     Task.eval {
    *       println("Received: " + elem.toString)
    *       elem * 2
    *     }
    *   }
    * }}}
    *
    * @param f is the mapping function that transforms the source
    *
    * @return a new iterant that's the result of mapping the given
    *         function over the source,
    */
  final def mapEval[B](f: A => F[B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantMapEval(this, f)(F)

  /** Given a predicate, finds the first item that satisfies it,
    * returning `Some(a)` if available, or `None` otherwise.
    *
    * {{{
    *   // Yields Some(2)
    *   Iterant[Coeval].of(1, 2, 3, 4).findL(_ % 2 == 0)
    *
    *   // Yields None
    *   Iterant[Coeval].of(1, 2, 3, 4).findL(_ > 10)
    * }}}
    *
    * The stream is traversed from beginning to end, the process
    * being interrupted as soon as it finds one element matching
    * the predicate, or until the stream ends.
    *
    * @param p is the function to test the elements of the source
    *
    * @return either `Some(value)` in case `value` is an element
    *         emitted by the source, found to satisfy the predicate,
    *         or `None` otherwise
    */
  def findL(p: A => Boolean)(implicit F: Sync[F]): F[Option[A]] = {
    val init = Option.empty[A]
    val next = Left(init)
    foldWhileLeftL(init) { (_, a) => if (p(a)) Right(Some(a)) else next }
  }

  /** Given evidence that type `A` has a `cats.Monoid` implementation,
    * folds the stream with the provided monoid definition.
    *
    * For streams emitting numbers, this effectively sums them up.
    * For strings, this concatenates them.
    *
    * Example:
    *
    * {{{
    *   // Yields 10
    *   Iterant[Task].of(1, 2, 3, 4).foldL
    *
    *   // Yields "1234"
    *   Iterant[Task].of("1", "2", "3", "4").foldL
    * }}}
    *
    * Note, in case you don't have a `Monoid` instance in scope,
    * but you feel like you should, try this import:
    *
    * {{{
    *   import cats.instances.all._
    * }}}
    *
    * @param A is the `cats.Monoid` type class instance that's needed
    *          in scope for folding the source
    *
    * @return the result of combining all elements of the source,
    *         or the defined `Monoid.empty` element in case the
    *         stream is empty
    */
  final def foldL(implicit F: Sync[F], A: Monoid[A]): F[A] =
    foldLeftL(A.empty)(A.combine)

  /** Left associative fold using the function `op`.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    *
    * Example: {{{
    *   // Yields 15 (1 + 2 + 3 + 4 + 5)
    *   Iterant[Task].of(1, 2, 3, 4, 5).foldLeftL(0)(_ + _)
    * }}}
    *
    * @param seed is the start value
    * @param op is the binary operator
    *
    * @return the result of inserting `op` between consecutive
    *         elements of this iterant, going from left to right with
    *         the `seed` as the start value, or `seed` if the iterant
    *         is empty.
    */
  final def foldLeftL[S](seed: => S)(op: (S, A) => S)(implicit F: Sync[F]): F[S] =
    IterantFoldLeft(self, seed)(op)(F)

  /** Left associative fold using the function `op` that can be
    * short-circuited.
    *
    * On execution the stream will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state either until the end, or until `op` returns
    * a `Right` result, when the summary is returned.
    *
    * The results are returned in the `F[_]` functor context, meaning
    * that we can have lazy or asynchronous processing and we can
    * suspend side effects, depending on the `F` data type being used.
    *
    * Example using `cats.effect.IO`: {{{
    *   // Sums first 10 items
    *   Iterant[IO].range(0, 1000).foldWhileLeftEvalL(IO((0, 0))) {
    *     case ((sum, count), e) =>
    *       IO {
    *         val next = (sum + e, count + 1)
    *         if (count + 1 < 10) Left(next) else Right(next)
    *       }
    *   }
    *
    *   // Implements exists(predicate)
    *   Iterant[IO].of(1, 2, 3, 4, 5).foldWhileLeftEvalL(IO(false)) {
    *     (default, e) =>
    *       IO { if (e == 3) Right(true) else Left(default) }
    *   }
    *
    *   // Implements forall(predicate)
    *   Iterant[IO].of(1, 2, 3, 4, 5).foldWhileLeftEvalL(IO(true)) {
    *     (default, e) =>
    *       IO { if (e != 3) Right(false) else Left(default) }
    *   }
    * }}}
    *
    * @see [[Iterant.foldWhileLeftL]] for the strict version.
    *
    * @param seed is the start value
    * @param op is the binary operator returning either `Left`,
    *        signaling that the state should be evolved or a `Right`,
    *        signaling that the process can be short-circuited and
    *        the result returned immediately
    *
    * @return the result of inserting `op` between consecutive
    *         elements of this iterant, going from left to right with
    *         the `seed` as the start value, or `seed` if the iterant
    *         is empty
    */
  final def foldWhileLeftEvalL[S](seed: F[S])(op: (S, A) => F[Either[S, S]])(implicit F: Sync[F]): F[S] =
    IterantFoldWhileLeft.eval(self, seed, op)

  /**
    * Lazily fold the stream to a single value from the right.
    *
    * This is the common `foldr` operation from Haskell's `Foldable`,
    * or `foldRight` from Scala's collections, however it has a twist:
    * the user is responsible for invoking early `stop` in case the
    * processing is short-circuited, hence the signature of function
    * `f` is different from other implementations, receiving the
    * current `earlyStop: F[Unit]` as a third parameter.
    *
    * Here's for example how [[existsL]], [[forallL]] and `++` could
    * be expressed in terms of `foldRightL`:
    *
    * {{{
    *   def exists[F[_], A](fa: Iterant[F, A], p: A => Boolean)
    *     (implicit F: Sync[F]): F[Boolean] = {
    *
    *     fa.foldRightL(F.pure(false)) { (a, next, stop) =>
    *       if (p(a)) stop.map(_ => true) else next
    *     }
    *   }
    *
    *   def forall[F[_], A](fa: Iterant[F, A], p: A => Boolean)
    *     (implicit F: Sync[F]): F[Boolean] = {
    *
    *     fa.foldRightL(F.pure(true)) { (a, next, stop) =>
    *       if (!p(a)) stop.map(_ => false) else next
    *     }
    *   }
    *
    *   def concat[F[_], A](lh: Iterant[F, A], rh: Iterant[F, A])
    *     (implicit F: Sync[F]): Iterant[F, A] = {
    *
    *     Iterant.suspend[F, A] {
    *       lh.foldRightL(F.pure(rh)) { (a, rest, stop) =>
    *         F.pure(Iterant.nextS(a, rest, stop))
    *       }
    *     }
    *   }
    * }}}
    *
    * In this example we are short-circuiting the processing in case
    * we find the one element that we are looking for, otherwise we
    * keep traversing the stream until the end, finally returning
    * the default value in case we haven't found what we were looking
    * for.
    *
    * ==WARNING==
    *
    * The implementation cannot ensure resource safety
    * automatically, therefore it falls on the user to chain the
    * `stop` reference in the processing, in case the right parameter
    * isn't factored in.
    *
    * In other words:
    *
    *  - in case the processing fails in any way with exceptions,
    *    it is the user's responsibility to chain `stop`
    *  - in case the processing is short-circuited by not using the
    *    `F[B]` right param, it is the user responsibility to chain
    *    `stop`
    *
    * This is in contrast with all operators (unless explicitly
    * mentioned otherwise).
    *
    * See the examples provided above, as they are correct in their
    * handling of `stop`.
    *
    * @see [[foldWhileLeftL]] and [[foldWhileLeftEvalL]] for safer
    *     alternatives in most cases
    *
    * @param b is the starting value; in case `f` is a binary operator,
    *        this is typically its left-identity (zero)
    *
    * @param f is the function to be called that folds the list,
    *        receiving the current element being iterated on
    *        (first param), the (lazy) result from recursively
    *        combining the rest of the list (second param) and
    *        the `earlyStop` routine, to chain in case
    *        short-circuiting should happen (third param)
    */
  final def foldRightL[B](b: F[B])(f: (A, F[B], F[Unit]) => F[B])(implicit F: Sync[F]): F[B] =
    IterantFoldRightL(self, b, f)(F)

  /** Given mapping functions from `F` to `G`, lifts the source into
    * an iterant that is going to use the resulting `G` for evaluation.
    *
    * This can be used for replacing the underlying `F` type into
    * something else. For example say we have an iterant that uses
    * [[monix.eval.Coeval Coeval]], but we want to convert it into
    * one that uses [[monix.eval.Task Task]] for evaluation:
    *
    * {{{
    *   // Source is using Coeval for evaluation
    *   val source = Iterant[Coeval].of(1, 2, 3, 4)
    *
    *   // Transformation to an iterant based on Task
    *   source.liftMap(_.toTask, _.toTask)
    * }}}
    *
    * @param f1 is the functor transformation used for transforming
    *          `rest` references
    * @param f2 is the mapping function for early `stop` references
    *
    * @tparam G is the data type that is going to drive the evaluation
    *           of the resulting iterant
    */
  final def liftMap[G[_]](f1: F[Iterant[F, A]] => G[Iterant[F, A]], f2: F[Unit] => G[Unit])
    (implicit F: Applicative[F], G: Sync[G]): Iterant[G, A] =
    IterantLiftMap(self, f1, f2)(F, G)

  /** Given a functor transformation from `F` to `G`, lifts the source
    * into an iterant that is going to use the resulting `G` for
    * evaluation.
    *
    * This can be used for replacing the underlying `F` type into
    * something else. For example say we have an iterant that uses
    * [[monix.eval.Coeval Coeval]], but we want to convert it into
    * one that uses [[monix.eval.Task Task]] for evaluation:
    *
    * {{{
    *   import cats.~>
    *
    *   // Source is using Coeval for evaluation
    *   val source = Iterant[Coeval].of(1, 2, 3, 4)
    *
    *   // Transformation to an iterant based on Task
    *   source.liftMapK(new (Coeval ~> Task) {
    *     def apply[A](fa: Coeval[A]): Task[A] =
    *       fa.task
    *   })
    * }}}
    *
    * This operator can be used for more than transforming the `F`
    * type into something else.
    *
    * @param f is the functor transformation that's used to transform
    *          the source into an iterant that uses `G` for evaluation
    *
    * @tparam G is the data type that is going to drive the evaluation
    *           of the resulting iterant
    */
  final def liftMapK[G[_]](f: FunctionK[F, G])(implicit G: Sync[G]): Iterant[G, A] =
    IterantLiftMap(self, f)(G)

  /** Takes the elements of the source iterant and emits the
    * element that has the maximum key value, where the key is
    * generated by the given function.
    *
    * Example:
    * {{{
    *   case class Person(name: String, age: Int)
    *
    *   // Yields Some(Person("Peter", 23))
    *   Iterant[Coeval].of(Person("Peter", 23), Person("May", 21))
    *     .maxByL(_.age)
    *
    *   // Yields None
    *   Iterant[Coeval].empty[Int].maxByL(_.age)
    * }}}
    *
    * @param key is the function that returns the key for which the
    *            given ordering is defined
    *
    * @param K  is the `cats.Order` type class instance that's going
    *           to be used for comparing elements
    *
    * @return the maximum element of the source stream, relative
    *         to its key generated by the given function and the
    *         given ordering
    */
  final def maxByL[K](key: A => K)(implicit F: Sync[F], K: Order[K]): F[Option[A]] =
    reduceL((max, a) => if (K.compare(key(max), key(a)) < 0) a else max)

  /** Given a `cats.Order` over the stream's elements, returns the
    * maximum element in the stream.
    *
    * Example:
    * {{{
    *   // Yields Some(20)
    *   Iterant[Coeval].of(1, 10, 7, 6, 8, 20, 3, 5).maxL
    *
    *   // Yields None
    *   Iterant[Coeval].empty[Int].maxL
    * }}}
    *
    * @param A is the `cats.Order` type class instance that's going
    *          to be used for comparing elements
    *
    * @return the maximum element of the source stream, relative
    *         to the defined `Order`
    */
  final def maxL(implicit F: Sync[F], A: Order[A]): F[Option[A]] =
    reduceL((max, a) => if (A.compare(max, a) < 0) a else max)

  /** Takes the elements of the source iterant and emits the
    * element that has the minimum key value, where the key is
    * generated by the given function.
    *
    * Example:
    * {{{
    *   case class Person(name: String, age: Int)
    *
    *   // Yields Some(Person("May", 21))
    *   Iterant[Coeval].of(Person("Peter", 23), Person("May", 21))
    *     .minByL(_.age)
    *
    *   // Yields None
    *   Iterant[Coeval].empty[Int].minByL(_.age)
    * }}}
    *
    * @param key is the function that returns the key for which the
    *            given ordering is defined
    *
    * @param K  is the `cats.Order` type class instance that's going
    *           to be used for comparing elements
    *
    * @return the minimum element of the source stream, relative
    *         to its key generated by the given function and the
    *         given ordering
    */
  final def minByL[K](key: A => K)(implicit F: Sync[F], K: Order[K]): F[Option[A]] =
    reduceL((max, a) => if (K.compare(key(max), key(a)) > 0) a else max)

  /** Given a `cats.Order` over the stream's elements, returns the
    * minimum element in the stream.
    *
    * Example:
    * {{{
    *   // Yields Some(3)
    *   Iterant[Coeval].of(10, 7, 6, 8, 20, 3, 5).minL
    *
    *   // Yields None
    *   Iterant[Coeval].empty[Int].minL
    * }}}
    *
    * @param A is the `cats.Order` type class instance that's going
    *          to be used for comparing elements
    *
    * @return the minimum element of the source stream, relative
    *         to the defined `Order`
    */
  final def minL(implicit F: Sync[F], A: Order[A]): F[Option[A]] =
    reduceL((max, a) => if (A.compare(max, a) > 0) a else max)

  /** Reduces the elements of the source using the specified
    * associative binary operator, going from left to right, start to
    * finish.
    *
    * Example:
    *
    * {{{
    *   // Yields Some(10)
    *   Iterant[Coeval].of(1, 2, 3, 4).reduceL(_ + _)
    *
    *   // Yields None
    *   Iterant[Coeval].empty[Int].reduceL(_ + _)
    * }}}
    *
    * @param op is an associative binary operation that's going
    *           to be used to reduce the source to a single value
    *
    * @return either `Some(value)` in case the stream is not empty,
    *         `value` being the result of inserting `op` between
    *         consecutive elements of this iterant, going from left
    *         to right, or `None` in case the stream is empty
    */
  final def reduceL(op: (A, A) => A)(implicit F: Sync[F]): F[Option[A]] =
    IterantReduce(self, op)

  /** Returns an `Iterant` that mirrors the behavior of the source,
    * unless the source is terminated with an error, in which case
    * the streaming of events continues with the specified backup
    * sequence generated by the given partial function.
    *
    * The created `Iterant` mirrors the behavior of the source in
    * case the source does not end with an error or if the thrown
    * `Throwable` is not matched.
    *
    * Example: {{{
    *   val prefix = Iterant[Task].of(1, 2, 3, 4)
    *   val suffix = Iterant[Task].raiseError(DummyException("dummy"))
    *   val fa = prefix ++ suffix
    *
    *   fa.onErrorRecoverWith {
    *     case _: DummyException =>
    *       Iterant[Task].pure(5)
    *   }
    * }}}
    *
    * See [[onErrorHandleWith]] for the version that takes a total
    * function as a parameter.
    *
    * @param pf is a function that matches errors with a
    *        backup throwable that is subscribed when the source
    *        throws an error.
    */
  final def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Iterant[F, B]])(implicit F: Sync[F]): Iterant[F, B] =
    onErrorHandleWith { ex =>
      if (pf.isDefinedAt(ex)) pf(ex)
      else Iterant.raiseError[F, B](ex)
    }

  /** Returns an `Iterant` that mirrors the behavior of the source,
    * unless the source is terminated with an error, in which case
    * the streaming of events continues with the specified backup
    * sequence generated by the given function.
    *
    * Example: {{{
    *   val prefix = Iterant[Task].of(1, 2, 3, 4)
    *   val suffix = Iterant[Task].raiseError(DummyException("dummy"))
    *   val fa = prefix ++ suffix
    *
    *   fa.onErrorHandleWith {
    *     case _: DummyException =>
    *       Iterant[Task].pure(5)
    *     case other =>
    *       Iterant[Task].raiseError(other)
    *   }
    * }}}
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial
    * function as a parameter.
    *
    * @param f is a function that matches errors with a
    *        backup throwable that is subscribed when the source
    *        throws an error.
    */
  final def onErrorHandleWith[B >: A](f: Throwable => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantOnError.handleWith(self.upcast, f)

  /** Returns an `Iterant` that mirrors the behavior of the source,
    * unless the source is terminated with an error, in which
    * case the streaming of events fallbacks to an iterant
    * emitting a single element generated by the backup function.
    *
    * The created `Iterant` mirrors the behavior of the source
    * in case the source does not end with an error or if the
    * thrown `Throwable` is not matched.
    *
    * Example: {{{
    *   val prefix = Iterant[Task].of(1, 2, 3, 4)
    *   val suffix = Iterant[Task].raiseError(DummyException("dummy"))
    *   val fa = prefix ++ suffix
    *
    *   fa.onErrorRecover {
    *     case _: DummyException => 5
    *   }
    * }}}
    *
    * See [[onErrorHandle]] for the version that takes a
    * total function as a parameter.
    *
    * @param pf - a function that matches errors with a
    *        backup element that is emitted when the source
    *        throws an error.
    */
  final def onErrorRecover[B >: A](pf: PartialFunction[Throwable, B])(implicit F: Sync[F]): Iterant[F, B] =
    onErrorHandle { e =>
      if (pf.isDefinedAt(e)) pf(e)
      else throw e
    }

  /** Returns an `Iterant` that mirrors the behavior of the source,
    * unless the source is terminated with an error, in which
    * case the streaming of events fallbacks to an iterant
    * emitting a single element generated by the backup function.
    *
    * Example: {{{
    *   val prefix = Iterant[Task].of(1, 2, 3, 4)
    *   val suffix = Iterant[Task].raiseError(DummyException("dummy"))
    *   val fa = prefix ++ suffix
    *
    *   fa.onErrorHandle { _ => 5 }
    * }}}
    *
    * See [[onErrorRecover]] for the version that takes a
    * partial function as a parameter.
    *
    * @param f is a function that matches errors with a
    *        backup element that is emitted when the source
    *        throws an error.
    */
  final def onErrorHandle[B >: A](f: Throwable => B)(implicit F: Sync[F]): Iterant[F, B] =
    onErrorHandleWith { e => Iterant.pure[F, B](f(e)) }

  /** Returns a new `Iterant` that mirrors the source, but ignores
    * any errors in case they happen.
    */
  final def onErrorIgnore(implicit F: Sync[F]): Iterant[F, A] =
    onErrorHandleWith(_ => Iterant.empty[F, A])

  /** Lazily zip two iterants together, the elements of the emitted
    * tuples being fetched in parallel.
    *
    * This is the parallel version of [[zip]], the results are
    * still ordered, but it can yield non-deterministic ordering
    * of effects when fetching the elements of an emitted tuple.
    *
    * @param rhs is the other iterant to zip the source with (the
    *        right hand side)
    */
  final def parZip[G[_], B](rhs: Iterant[F, B])
    (implicit F: Sync[F], P: Parallel[F, G]): Iterant[F, (A, B)] =
    (self parZipMap rhs) ((a, b) => (a, b))

  /** Lazily zip two iterants together, in parallel, using the given
    * function `f` to produce output values.
    *
    * This is like [[zipMap]], except that the element pairs are
    * processed in parallel (ordered results, but non-deterministic
    * ordering of effects).
    *
    * @param rhs is the other iterant to zip the source with (the
    *        right hand side)
    *
    * @param f is the mapping function to transform the zipped
    *        `(A, B)` elements
    */
  final def parZipMap[G[_], B, C](rhs: Iterant[F, B])(f: (A, B) => C)
    (implicit F: Sync[F], P: Parallel[F, G]): Iterant[F, C] =
    IterantZipMap.par(this, rhs, f)

  /** Applies the function to the elements of the source and
    * concatenates the results.
    *
    * This variant of [[flatMap]] is not referentially transparent,
    * because it tries to apply function `f` immediately, in case the
    * `Iterant` is in a `NextCursor` or `NextBatch` state.
    *
    * To be used for optimizations, but keep in mind it's unsafe, as
    * its application isn't referentially transparent.
    *
    * @param f is the function mapping elements from the source to
    *        iterants
    */
  final def unsafeFlatMap[B](f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
    IterantConcat.unsafeFlatMap(this)(f)(F)

  /** Creates a new iterant that upon evaluation will select
    * the first `n` elements from the source and then stop,
    * in the order they are emitted by the source.
    *
    * Example: {{{
    *   // Yields 1, 2, 3
    *   Iterant[Task].of(1, 2, 3, 4, 5, 6).take(3)
    * }}}
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
    *
    * Example: {{{
    *   // Yields 1, 2, 3
    *   Iterant[Task].of(1, 2, 3, 4, 5, 6).take(3)
    * }}}
    *
    * @param n is the number of elements to take from the end of the
    *        stream.
    *
    * @return a new iterant instance that on evaluation will emit the
    *         last `n` elements of the source
    */
  final def takeLast(n: Int)(implicit F: Sync[F]): Iterant[F, A] =
    IterantTakeLast(self, n)

  /** Takes longest prefix of elements that satisfy the given predicate
    * and returns a new iterant that emits those elements.
    *
    * Example: {{{
    *   // Yields 1, 2, 3
    *   Iterant[Task].of(1, 2, 3, 4, 5, 6).takeWhile(_ < 4)
    * }}}
    *
    * @param p is the function that tests each element, stopping
    *          the streaming on the first `false` result
    *
    * @return a new iterant instance that on evaluation will all
    *         elements of the source for as long as the given predicate
    *         returns `true`, stopping upon the first `false` result
    */
  final def takeWhile(p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] =
    IterantTakeWhile(self, p)(F)

  /** Drops the first element of the source iterant, emitting the rest.
    *
    * Example: {{{
    *   // Yields 2, 3, 4
    *   Iterant[Task].of(1, 2, 3, 4).tail
    * }}}
    *
    * @return a new iterant that upon evaluation will emit all
    *         elements of the source, except for the head
    */
  final def tail(implicit F: Sync[F]): Iterant[F, A] =
    IterantTail(self)(F)

  /** Converts this `Iterant` into an `org.reactivestreams.Publisher`.
    *
    * Meant for interoperability with other Reactive Streams
    * implementations. Also useful because it turns the `Iterant`
    * into another data type with a push-based communication protocol
    * with back-pressure.
    *
    * Usage sample:
    *
    * {{{
    *   import monix.eval.Task
    *   import monix.execution.rstreams.SingleAssignmentSubscription
    *   import org.reactivestreams.{Publisher, Subscriber, Subscription}
    *
    *   def sum(source: Publisher[Int], requestSize: Int): Task[Long] =
    *     Task.create { (_, cb) =>
    *       val sub = SingleAssignmentSubscription()
    *
    *       source.subscribe(new Subscriber[Int] {
    *         private[this] var requested = 0L
    *         private[this] var sum = 0L
    *
    *         def onSubscribe(s: Subscription): Unit = {
    *           sub := s
    *           requested = requestSize
    *           s.request(requestSize)
    *         }
    *
    *         def onNext(t: Int): Unit = {
    *           sum += t
    *           if (requestSize != Long.MaxValue) requested -= 1
    *
    *           if (requested <= 0) {
    *             requested = requestSize
    *             sub.request(request)
    *           }
    *         }
    *
    *         def onError(t: Throwable): Unit =
    *           cb.onError(t)
    *         def onComplete(): Unit =
    *           cb.onSuccess(sum)
    *       })
    *
    *       // Cancelable that can be used by Task
    *       sub
    *     }
    *
    *   val pub = Iterant[Task].of(1, 2, 3, 4).toReactivePublisher
    *
    *   // Yields 10
    *   sum(pub, requestSize = 128)
    * }}}
    *
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * for details.
    */
  final def toReactivePublisher(implicit F: Effect[F], ec: Scheduler): Publisher[A] =
    IterantToReactivePublisher(self)(F, ec)

  /** Applies a binary operator to a start value and all elements of
    * this `Iterant`, going left to right and returns a new
    * `Iterant` that emits on each step the result of the applied
    * function.
    *
    * Similar to [[foldLeftL]], but emits the state on each
    * step. Useful for modeling finite state machines.
    *
    * Example showing how state can be evolved and acted upon:
    * {{{
    *   sealed trait State[+A] { def count: Int }
    *   case object Init extends State[Nothing] { def count = 0 }
    *   case class Current[A](current: A, count: Int) extends State[A]
    *
    *   val scanned = source.scan(Init : State[A]) { (acc, a) =>
    *     acc match {
    *       case Init => Current(a, 1)
    *       case Current(_, count) => Current(a, count + 1)
    *     }
    *   }
    *
    *   scanned
    *     .takeWhile(_.count < 10)
    *     .collect { case Current(a, _) => a }
    * }}}
    *
    * @param seed is the initial state
    * @param op is the function that evolves the current state
    *
    * @return a new iterant that emits all intermediate states being
    *         resulted from applying function `op`
    */
  final def scan[S](seed: => S)(op: (S, A) => S)(implicit F: Sync[F]): Iterant[F, S] =
    IterantScan(self, seed, op)

  /** Applies a binary operator to a start value and all elements of
    * this `Iterant`, going left to right and returns a new
    * `Iterant` that emits on each step the result of the applied
    * function.
    *
    * Similar with [[scan]], but this can suspend and evaluate
    * side effects in the `F[_]` context, thus allowing for
    * asynchronous data processing.
    *
    * Similar to [[foldLeftL]] and [[foldWhileLeftEvalL]], but
    * emits the state on each step. Useful for modeling finite
    * state machines.
    *
    * Example showing how state can be evolved and acted upon:
    *
    * {{{
    *   sealed trait State[+A] { def count: Int }
    *   case object Init extends State[Nothing] { def count = 0 }
    *   case class Current[A](current: Option[A], count: Int)
    *     extends State[A]
    *
    *   case class Person(id: Int, name: String)
    *
    *   // Initial state
    *   val seed = Task.now(Init : State[Person])
    *
    *   val scanned = source.scanEval(seed) { (state, id) =>
    *     requestPersonDetails(id).map { person =>
    *       state match {
    *         case Init =>
    *           Current(person, 1)
    *         case Current(_, count) =>
    *           Current(person, count + 1)
    *       }
    *     }
    *   }
    *
    *   scanned
    *     .takeWhile(_.count < 10)
    *     .collect { case Current(a, _) => a }
    * }}}
    *
    * @see [[scan]] for the version that does not require using `F[_]`
    *      in the provided operator
    *
    * @param seed is the initial state
    * @param op is the function that evolves the current state
    *
    * @return a new iterant that emits all intermediate states being
    *         resulted from applying the given function
    */
  final def scanEval[S](seed: F[S])(op: (S, A) => F[S])(implicit F: Sync[F]): Iterant[F, S] =
    IterantScanEval(self, seed, op)

  /** Skips over [[Iterant.Suspend]] states, along with
    * [[Iterant.NextCursor]] and [[Iterant.NextBatch]] states that
    * signal empty collections.
    *
    * Will mirror the source, except that the emitted internal states
    * might be different. Can be used as an optimization if necessary.
    */
  final def skipSuspendL(implicit F: Sync[F]): F[Iterant[F, A]] =
    IterantSkipSuspend(self)

  /** Aggregates all elements in a `List` and preserves order.
    *
    * Example: {{{
    *   // Yields List(1, 2, 3, 4)
    *   Iterant[Task].of(1, 2, 3, 4).toListL
    * }}}
    *
    * Note that this operation is dangerous, since if the iterant is
    * infinite then this operation is non-terminating, the process
    * probably blowing up with an out of memory error sooner or later.
    */
  final def toListL(implicit F: Sync[F]): F[List[A]] =
    IterantFoldLeft.toListL(self)(F)

  /** Lazily zip two iterants together.
    *
    * The length of the result will be the shorter of the two
    * arguments.
    *
    * Example: {{{
    *   val lh = Iterant[Task].of(11, 12, 13, 14)
    *   val rh = Iterant[Task].of(21, 22, 23, 24, 25)
    *
    *   // Yields (11, 21), (12, 22), (13, 23), (14, 24)
    *   lh.zip(rh)
    * }}}
    *
    * @param rhs is the other iterant to zip the source with (the
    *        right hand side)
    */
  final def zip[B](rhs: Iterant[F, B])(implicit F: Sync[F]): Iterant[F, (A, B)] =
    (self zipMap rhs) ((a, b) => (a, b))

  /** Lazily zip two iterants together, using the given function `f` to
    * produce output values.
    *
    * The length of the result will be the shorter of the two
    * arguments.
    *
    * Example: {{{
    *   val lh = Iterant[Task].of(11, 12, 13, 14)
    *   val rh = Iterant[Task].of(21, 22, 23, 24, 25)
    *
    *   // Yields 32, 34, 36, 38
    *   lh.zipMap(rh) { (a, b) => a + b }
    * }}}
    *
    * @param rhs is the other iterant to zip the source with (the
    *        right hand side)
    *
    * @param f is the mapping function to transform the zipped
    *        `(A, B)` elements
    */
  final def zipMap[B, C](rhs: Iterant[F, B])(f: (A, B) => C)
    (implicit F: Sync[F]): Iterant[F, C] =
    IterantZipMap.seq(this, rhs, f)

  /** Zips the emitted elements of the source with their indices.
    *
    * The length of the result will be the same as the source.
    *
    * Example: {{{
    *   val source = Iterant[Task].of("Sunday", "Monday", "Tuesday", "Wednesday")
    *
    *   // Yields ("Sunday", 0), ("Monday", 1), ("Tuesday", 2), ("Wednesday", 3)
    *   source.zipWithIndex
    * }}}
    */
  final def zipWithIndex(implicit F: Sync[F]): Iterant[F, (A, Long)] =
    IterantZipWithIndex(this)
}

/** Defines the standard [[Iterant]] builders.
  *
  * @define NextDesc The [[monix.tail.Iterant.Next Next]] state
  *         of the [[Iterant]] represents a `head` / `rest`
  *         cons pair, where the `head` is a strict value.
  *
  *         Note the `head` being a strict value means that it is
  *         already known, whereas the `rest` is meant to be lazy and
  *         can have asynchronous behavior as well, depending on the `F`
  *         type used.
  *
  *         See [[monix.tail.Iterant.NextCursor NextCursor]]
  *         for a state where the head is a strict immutable list.
  *
  * @define NextCursorDesc The [[monix.tail.Iterant.NextCursor NextCursor]] state
  *         of the [[Iterant]] represents an `batch` / `rest` cons pair,
  *         where `batch` is an [[scala.collection.Iterator Iterator]]
  *         type that can generate a whole batch of elements.
  *
  *         Useful for doing buffering, or by giving it an empty iterator,
  *         useful to postpone the evaluation of the next element.
  *
  * @define NextBatchDesc The [[monix.tail.Iterant.NextBatch NextBatch]] state
  *         of the [[Iterant]] represents an `batch` / `rest` cons pair,
  *         where `batch` is an [[scala.collection.Iterable Iterable]]
  *         type that can generate a whole batch of elements.
  *
  * @define SuspendDesc The [[monix.tail.Iterant.Suspend Suspend]] state
  *         of the [[Iterant]] represents a suspended stream to be
  *         evaluated in the `F` context. It is useful to delay the
  *         evaluation of a stream by deferring to `F`.
  *
  * @define LastDesc The [[monix.tail.Iterant.Last Last]] state of the
  *         [[Iterant]] represents a completion state as an alternative to
  *         [[monix.tail.Iterant.Halt Halt(None)]], describing one
  *         last element.
  *
  *         It is introduced as an optimization, being equivalent to
  *         `Next(item, F.pure(Halt(None)), F.unit)`, to avoid extra processing
  *         in the monadic `F[_]` and to short-circuit operations such as
  *         concatenation and `flatMap`.
  *
  * @define HaltDesc The [[monix.tail.Iterant.Halt Halt]] state
  *         of the [[Iterant]] represents the completion state
  *         of a stream, with an optional exception if an error
  *         happened.
  *
  *         `Halt` is received as a final state in the iteration process.
  *         This state cannot be followed by any other element and
  *         represents the end of the stream.
  *
  * @see [[Iterant.Last]] for an alternative that signals one
  *              last item, as an optimisation
  *
  * @define builderSuspendByName Promote a non-strict value representing a
  *         stream to a stream of the same type, effectively delaying
  *         its initialisation.
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
object Iterant extends IterantInstances {
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

  /** Lifts a strict value into the stream context, returning a
    * stream of one element.
    */
  def now[F[_], A](a: A): Iterant[F, A] =
    lastS(a)

  /** Builds a stream state equivalent with [[Iterant.Last]].
    *
    * $LastDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[F[_], A](item: A): Iterant[F, A] =
    Last(item)

  /** Lifts a non-strict value into the stream context, returning a
    * stream of one element that is lazily evaluated.
    */
  def eval[F[_], A](a: => A)(implicit F: Sync[F]): Iterant[F, A] =
    Suspend(F.delay(nextS[F, A](a, F.pure(Halt(None)), F.unit)), F.unit)

  /** Builds a stream state equivalent with [[Iterant.Next]].
    *
    * $NextDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[F[_], A](item: A, rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Next[F, A](item, rest, stop)

  /** Builds a stream state equivalent with [[Iterant.NextCursor]].
    *
    * $NextCursorDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextCursorS[F[_], A](items: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    NextCursor[F, A](items, rest, stop)

  /** Builds a stream state equivalent with [[Iterant.NextBatch]].
    *
    * $NextBatchDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextBatchS[F[_], A](items: Batch[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    NextBatch[F, A](items, rest, stop)

  /** Builds a stream state equivalent with [[Iterant.Halt]].
    *
    * $HaltDesc
    *
    * @param e $exParamDesc
    */
  def haltS[F[_], A](e: Option[Throwable]): Iterant[F, A] =
    Halt[F, A](e)

  /** Alias for [[Iterant.suspend[F[_],A](fa* suspend]].
    *
    * $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def defer[F[_], A](fa: => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    suspend(fa)

  /** $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def suspend[F[_], A](fa: => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    suspend[F, A](F.delay(fa))

  /** Defers the stream generation to the underlying evaluation
    * context (e.g. `Task`, `Coeval`, `IO`, etc), building a reference
    * equivalent with [[Iterant.Suspend]].
    *
    * $SuspendDesc
    *
    * @param rest $restParamDesc
    */
  def suspend[F[_], A](rest: F[Iterant[F, A]])(implicit F: Applicative[F]): Iterant[F, A] =
    suspendS[F, A](rest, F.unit)

  /** Builds a stream state equivalent with [[Iterant.NextCursor]].
    *
    * $SuspendDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[F[_], A](rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Suspend[F, A](rest, stop)

  /** Returns an empty stream that ends with an error. */
  def raiseError[F[_], A](ex: Throwable): Iterant[F, A] =
    Halt[F, A](Some(ex))

  /** Keeps calling `f` and concatenating the resulting iterants for
    * each `scala.util.Left` event emitted by the source,
    * concatenating the resulting iterants and generating
    * events out of `scala.util.Right[B]` values.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    */
  def tailRecM[F[_], A, B](a: A)(f: A => Iterant[F, Either[A, B]])(implicit F: Sync[F]): Iterant[F, B] =
    IterantConcat.tailRecM(a)(f)

  /** Converts any standard `Array` into a stream. */
  def fromArray[F[_], A: ClassTag](xs: Array[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextBatch(Batch.fromArray(xs), F.pure(empty[F, A]), F.unit)

  /** Converts any `scala.collection.Seq` into a stream. */
  def fromSeq[F[_], A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    xs match {
      case ref: LinearSeq[_] =>
        fromList[F, A](ref.asInstanceOf[LinearSeq[A]])(F)
      case ref: IndexedSeq[_] =>
        fromIndexedSeq[F, A](ref.asInstanceOf[IndexedSeq[A]])(F)
      case _ =>
        fromIterable(xs)(F)
    }

  /** Converts any Scala `collection.immutable.LinearSeq` into
    * a stream.
    */
  def fromList[F[_], A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextBatch(Batch.fromSeq(xs), F.pure(empty[F, A]), F.unit)

  /** Converts any Scala `collection.IndexedSeq` into a stream
    * (e.g. `Vector`).
    */
  def fromIndexedSeq[F[_], A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    NextBatch(Batch.fromIndexedSeq(xs), F.pure(empty[F, A]), F.unit)

  /** Converts a `scala.collection.Iterable` into a stream. */
  def fromIterable[F[_], A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F, A] = {
    val bs = if (xs.hasDefiniteSize) batches.defaultBatchSize else 1
    NextBatch(Batch.fromIterable(xs, bs), F.pure(empty[F, A]), F.unit)
  }

  /** Converts a `scala.collection.Iterator` into a stream. */
  def fromIterator[F[_], A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F, A] = {
    val bs = if (xs.hasDefiniteSize) batches.defaultBatchSize else 1
    NextCursor[F, A](BatchCursor.fromIterator(xs, bs), F.pure(empty), F.unit)
  }

  /** Builds a stream that on evaluation will produce equally spaced
    * values in some integer interval.
    *
    * @param from the start value of the stream
    * @param until the end value of the stream (exclusive from the stream)
    * @param step the increment value of the tail (must be positive or negative)
    *
    * @return the tail producing values `from, from + step, ...` up
    *         to, but excluding `until`
    */
  def range[F[_]](from: Int, until: Int, step: Int = 1)(implicit F: Applicative[F]): Iterant[F, Int] =
    NextBatch(Batch.range(from, until, step), F.pure(empty[F, Int]), F.unit)

  /** Returns an empty stream. */
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

  /** Builds a stream state equivalent with [[Iterant.NextCursor]].
    *
    * $SuspendDesc
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
    * @param e $exParamDesc
    */
  final case class Halt[F[_], A](e: Option[Throwable])
    extends Iterant[F, A] {

    def earlyStop(implicit F: Applicative[F]): F[Unit] =
      F.unit
  }

}

private[tail] trait IterantInstances extends IterantInstances1 {
  /** Provides type class instances for `Iterant[Task, A]`, based
    * on the default instances provided by
    * [[monix.eval.Task.catsAsync Task.catsAsync]].
    */
  implicit def catsInstancesForTask(implicit F: Async[Task]): CatsInstances[Task] = {
    // Avoiding the creation of junk, because it is expensive
    F match {
      case _: CatsBaseForTask => defaultIterantTaskRef
      case _ => new CatsInstancesForTask()(F)
    }
  }

  /** Reusable instance for `Iterant[Task, A]`, avoids creating junk. */
  private[this] final val defaultIterantTaskRef: CatsInstances[Task] =
    new CatsInstancesForTask()(CatsAsyncForTask)

  /** Provides type class instances for `Iterant[Coeval, A]`, based on
    * the default instances provided by
    * [[monix.eval.Coeval.catsSync Coeval.catsSync]].
    */
  implicit def catsInstancesForCoeval(implicit F: Sync[Coeval]): CatsInstances[Coeval] = {
    // Avoiding the creation of junk, because it is expensive
    F match {
      case CatsSyncForCoeval => defaultIterantCoevalRef
      case _ => new CatsInstancesForCoeval()(F)
    }
  }

  /** Reusable instance for `Iterant[Coeval, A]`, avoids creating junk. */
  private[this] final val defaultIterantCoevalRef =
    new CatsInstancesForCoeval()(CatsSyncForCoeval)

  /** Provides type class instances for `Iterant[Task, A]`, based
    * on the default instances provided by
    * [[monix.eval.Task.catsAsync Task.catsAsync]].
    */
  private final class CatsInstancesForTask(implicit F: Async[Task])
    extends CatsInstances[Task]()(F)

  /** Provides type class instances for `Iterant[Coeval, A]`, based on
    * the default instances provided by
    * [[monix.eval.Coeval.catsSync Coeval.catsSync]].
    */
  private final class CatsInstancesForCoeval(implicit F: Sync[Coeval])
    extends CatsInstances[Coeval]()(F)

}

private[tail] trait IterantInstances1 {
  /** Provides a Cats type class instances for [[Iterant]]. */
  implicit def catsInstances[F[_]](implicit F: Sync[F]): CatsInstances[F] =
    new CatsInstances[F]()

  /** Provides a `cats.effect.Sync` instance for [[Iterant]]. */
  class CatsInstances[F[_]](implicit F: Sync[F])
    extends MonadError[({type λ[α] = Iterant[F, α]})#λ, Throwable]
      with MonoidK[({type λ[α] = Iterant[F, α]})#λ]
      with CoflatMap[({type λ[α] = Iterant[F, α]})#λ] {

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

    override def coflatMap[A, B](fa: Iterant[F, A])(f: (Iterant[F, A]) => B): Iterant[F, B] =
      Iterant.pure[F, B](f(fa))

    override def coflatten[A](fa: Iterant[F, A]): Iterant[F, Iterant[F, A]] =
      Iterant.pure(fa)

    override def raiseError[A](e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    override def attempt[A](fa: Iterant[F, A]): Iterant[F, Either[Throwable, A]] =
      fa.attempt

    override def handleErrorWith[A](fa: Iterant[F, A])(f: (Throwable) => Iterant[F, A]): Iterant[F, A] =
      fa.onErrorHandleWith(f)

    override def handleError[A](fa: Iterant[F, A])(f: (Throwable) => A): Iterant[F, A] =
      fa.onErrorHandle(f)

    override def recover[A](fa: Iterant[F, A])(pf: PartialFunction[Throwable, A]): Iterant[F, A] =
      fa.onErrorRecover(pf)

    override def recoverWith[A](fa: Iterant[F, A])(pf: PartialFunction[Throwable, Iterant[F, A]]): Iterant[F, A] =
      fa.onErrorRecoverWith(pf)
  }
}
