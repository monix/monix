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

import monix.types.Evaluable
import monix.eval.ConsStream._

import scala.collection.{LinearSeq, immutable, mutable}
import scala.util.control.NonFatal
import language.higherKinds

sealed abstract class ConsStream[+A, F[_]](implicit F: Evaluable[F])
  extends Product with Serializable {

  /** Filters the stream by the given predicate function,
    * returning only those elements that match.
    */
  final def filter(p: A => Boolean): ConsStream[A,F] =
    this match {
      case ref @ Next(head, tail) =>
        try { if (p(head)) ref else Wait[A,F](F.map(tail)(_.filter(p))) }
        catch { case NonFatal(ex) => Error(ex) }
      case NextSeq(head, tail) =>
        val rest = F.map(tail)(_.filter(p))
        try head.filter(p) match {
          case Nil => Wait[A,F](rest)
          case filtered => NextSeq[A,F](filtered, rest)
        } catch {
          case NonFatal(ex) => Error(ex)
        }
      case Wait(rest) => Wait[A,F](F.map(rest)(_.filter(p)))
      case empty @ Empty() => empty
      case error @ Error(ex) => error
    }

  /** Returns a new iterable by mapping the supplied function
    * over the elements of the source.
    */
  final def map[B](f: A => B): ConsStream[B,F] = {
    this match {
      case Next(head, tail) =>
        try { Next[B,F](f(head), F.map(tail)(_.map(f))) }
        catch { case NonFatal(ex) => Error(ex) }
      case NextSeq(head, rest) =>
        try { NextSeq[B,F](head.map(f), F.map(rest)(_.map(f))) }
        catch { case NonFatal(ex) => Error(ex) }

      case Wait(rest) => Wait[B,F](F.map(rest)(_.map(f)))
      case empty @ Empty() => empty
      case error @ Error(_) => error
    }
  }

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => ConsStream[B,F]): ConsStream[B,F] = {
    this match {
      case Next(head, tail) =>
        try { f(head) concatF F.map(tail)(_.flatMap(f)) }
        catch { case NonFatal(ex) => Error(ex) }

      case NextSeq(list, rest) =>
        try {
          if (list.isEmpty)
            Wait[B,F](F.map(rest)(_.flatMap(f)))
          else
            f(list.head) concatF F.evalAlways(NextSeq[A,F](list.tail, rest).flatMap(f))
        } catch {
          case NonFatal(ex) => Error(ex)
        }

      case Wait(rest) => Wait[B,F](F.map(rest)(_.flatMap(f)))
      case empty @ Empty() => empty
      case error @ Error(_) => error
    }
  }

  /** If the source is an async iterable generator, then
    * concatenates the generated async iterables.
    */
  final def flatten[B](implicit ev: A <:< ConsStream[B,F]): ConsStream[B,F] =
    flatMap(x => x)

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => ConsStream[B,F]): ConsStream[B,F] =
    flatMap(f)

  /** Alias for [[concat]]. */
  final def concat[B](implicit ev: A <:< ConsStream[B,F]): ConsStream[B,F] =
    flatten

  /** Appends the given iterable to the end of the source,
    * effectively concatenating them.
    */
  final def ++[B >: A](rhs: ConsStream[B,F]): ConsStream[B,F] =
    this match {
      case Wait(task) =>
        Wait[B,F](F.map(task)(_ ++ rhs))
      case Next(a, lt) =>
        Next[B,F](a, F.map(lt)(_ ++ rhs))
      case NextSeq(head, lt) =>
        NextSeq[B,F](head, F.map(lt)(_ ++ rhs))
      case Empty() => rhs
      case error @ Error(_) => error
    }

  private final def concatF[B >: A](rhs: F[ConsStream[B,F]]): ConsStream[B,F] = {
    this match {
      case Wait(task) =>
        Wait[B,F](F.map(task)(_ concatF rhs))
      case Next(a, lt) =>
        Next[B,F](a, F.map(lt)(_ concatF rhs))
      case NextSeq(head, lt) =>
        NextSeq[B,F](head, F.map(lt)(_ concatF rhs))
      case Empty() => Wait[B,F](rhs)
      case Error(ex) => Error(ex)
    }
  }

  /** Left associative fold using the function 'f'.
    *
    * On execution the iterable will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  final def foldLeftL[S](seed: S)(f: (S,A) => S): F[S] =
    this match {
      case Empty() => F.now(seed)
      case Error(ex) => F.error(ex)
      case Wait(next) =>
        F.flatMap(next)(_.foldLeftL(seed)(f))
      case Next(a, next) =>
        try {
          val state = f(seed, a)
          F.flatMap(next)(_.foldLeftL(state)(f))
        } catch {
          case NonFatal(ex) => F.error(ex)
        }
      case NextSeq(list, next) =>
        try {
          val state = list.foldLeft(seed)(f)
          F.flatMap(next)(_.foldLeftL(state)(f))
        } catch {
          case NonFatal(ex) => F.error(ex)
        }
    }

  /** Left associative fold with the ability to short-circuit the process.
    *
    * This fold works for as long as the provided function keeps returning `true`
    * as the first member of its result and the streaming isn't completed.
    * If the provided fold function returns a `false` then the folding will
    * stop and the generated result will be the second member
    * of the resulting tuple.
    *
    * @param f is the folding function, returning `(true, state)` if the fold has
    *          to be continued, or `(false, state)` if the fold has to be stopped
    *          and the rest of the values to be ignored.
    */
  final def foldWhileL[S](seed: S)(f: (S, A) => (Boolean, S)): F[S] =
    this match {
      case Empty() => F.now(seed)
      case Error(ex) => F.error(ex)
      case Wait(next) =>
        F.flatMap(next)(_.foldWhileL(seed)(f))
      case Next(a, next) =>
        try {
          val (continue, state) = f(seed, a)
          if (!continue) F.now(state) else
            F.flatMap(next)(_.foldWhileL(state)(f))
        } catch {
          case NonFatal(ex) => F.error(ex)
        }
      case NextSeq(list, next) =>
        try {
          var continue = true
          var state = seed
          val iter = list.iterator

          while (continue && iter.hasNext) {
            val (c,s) = f(state, iter.next())
            state = s
            continue = c
          }

          if (!continue) F.now(state) else
            F.flatMap(next)(_.foldWhileL(state)(f))
        } catch {
          case NonFatal(ex) => F.error(ex)
        }
    }

  /** Right associative lazy fold on stream using the
    * folding function 'f'.
    *
    * This method evaluates `lb` lazily (in some cases it will not be
    * needed), and returns a lazy value. We are using `(A, Eval[B]) =>
    * Eval[B]` to support laziness in a stack-safe way. Chained
    * computation should be performed via .map and .flatMap.
    *
    * For more detailed information about how this method works see the
    * documentation for `Eval[_]`.
    */
  final def foldRightL[B](lb: F[B])(f: (A, F[B]) => F[B]): F[B] =
    this match {
      case Empty() => lb
      case Error(ex) => F.error(ex)
      case Wait(next) =>
        F.flatMap(next)(_.foldRightL(lb)(f))
      case Next(a, next) =>
        f(a, F.flatMap(next)(_.foldRightL(lb)(f)))

      case NextSeq(list, next) =>
        if (list.isEmpty) F.flatMap(next)(_.foldRightL(lb)(f))
        else {
          val a = list.head
          val tail = list.tail
          val rest = F.now(NextSeq[A,F](tail, next))
          f(a, F.flatMap(rest)(_.foldRightL(lb)(f)))
        }
    }

  /** Find the first element matching the predicate, if one exists. */
  final def findL[B >: A](p: B => Boolean): F[Option[B]] =
    foldWhileL(Option.empty[B])((s,a) => if (p(a)) (false, Some(a)) else (true, s))

  /** Count the total number of elements. */
  final def countL: F[Long] =
    foldLeftL(0L)((acc,_) => acc + 1)

  /** Given a sequence of numbers, calculates a sum. */
  final def sumL[B >: A](implicit B: Numeric[B]): F[B] =
    foldLeftL(B.zero)(B.plus)

  /** Check whether at least one element satisfies the predicate. */
  final def existsL(p: A => Boolean): F[Boolean] =
    foldWhileL(false)((s,a) => if (p(a)) (false, true) else (true, s))

  /** Check whether all elements satisfy the predicate. */
  final def forallL(p: A => Boolean): F[Boolean] =
    foldWhileL(true)((s,a) => if (!p(a)) (false, false) else (true, s))

  /** Aggregates elements in a `List` and preserves order. */
  final def toListL[B >: A]: F[List[B]] = {
    val folded = foldLeftL(mutable.ListBuffer.empty[A]) { (acc, a) => acc += a }
    F.map(folded)(_.toList)
  }

  /** Returns true if there are no elements, false otherwise. */
  final def isEmptyL: F[Boolean] =
    foldWhileL(true)((_,_) => (false, false))

  /** Returns true if there are elements, false otherwise. */
  final def nonEmptyL: F[Boolean] =
    foldWhileL(false)((_,_) => (false, true))

  /** Returns the first element in the iterable, as an option. */
  final def headL[B >: A]: F[Option[B]] =
    this match {
      case Wait(next) => F.flatMap(next)(_.headL)
      case Empty() => F.now(None)
      case Error(ex) => F.error(ex)
      case Next(a, _) => F.now(Some(a))
      case NextSeq(list, _) => F.now(list.headOption)
    }

  /** Alias for [[headL]]. */
  final def firstL[B >: A]: F[Option[B]] = headL

  /** Returns a new sequence that will take a maximum of
    * `n` elements from the start of the source sequence.
    */
  final def take(n: Int): ConsStream[A,F] =
    if (n <= 0) Empty() else this match {
      case Wait(next) => Wait[A,F](F.map(next)(_.take(n)))
      case empty @ Empty() => empty
      case error @ Error(_) => error
      case Next(a, next) =>
        if (n - 1 > 0)
          Next[A,F](a, F.map(next)(_.take(n-1)))
        else
          Next[A,F](a, F.now(Empty()))

      case NextSeq(list, rest) =>
        val length = list.length
        if (length == n)
          NextSeq[A,F](list, F.now(Empty()))
        else if (length < n)
          NextSeq[A,F](list, F.map(rest)(_.take(n-length)))
        else
          NextSeq[A,F](list.take(n), F.now(Empty()))
    }

  /** Returns a new sequence that will take elements from
    * the start of the source sequence, for as long as the given
    * function `f` returns `true` and then stop.
    */
  final def takeWhile(p: A => Boolean): ConsStream[A,F] =
    this match {
      case Wait(next) => Wait[A,F](F.map(next)(_.takeWhile(p)))
      case empty @ Empty() => empty
      case error @ Error(_) => error
      case Next(a, next) =>
        try { if (p(a)) Next[A,F](a, F.map(next)(_.takeWhile(p))) else Empty() }
        catch { case NonFatal(ex) => Error(ex) }
      case NextSeq(list, rest) =>
        try {
          val filtered = list.takeWhile(p)
          if (filtered.length < list.length)
            NextSeq[A,F](filtered, F.now(Empty()))
          else
            NextSeq[A,F](filtered, F.map(rest)(_.takeWhile(p)))
        } catch {
          case NonFatal(ex) => Error(ex)
        }
    }

  /** Recovers from potential errors by mapping them to other
    * async iterators using the provided function.
    */
  final def onErrorHandleWith[B >: A](f: Throwable => ConsStream[B,F]): ConsStream[B,F] =
    this match {
      case empty @ Empty() => empty
      case Wait(next) => Wait[B,F](F.map(next)(_.onErrorHandleWith(f)))
      case Next(a, next) => Next[B,F](a, F.map(next)(_.onErrorHandleWith(f)))
      case NextSeq(seq, next) => NextSeq[B,F](seq, F.map(next)(_.onErrorHandleWith(f)))
      case Error(ex) => try f(ex) catch { case NonFatal(err) => Error(err) }
    }

  /** Recovers from potential errors by mapping them to other
    * async iterators using the provided function.
    */
  final def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, ConsStream[B,F]]): ConsStream[B,F] =
    onErrorHandleWith {
      case ex if pf.isDefinedAt(ex) => pf(ex)
      case other => Error(other)
    }

  /** Recovers from potential errors by mapping them to
    * a final element using the provided function.
    */
  final def onErrorHandle[B >: A](f: Throwable => B): ConsStream[B,F] =
    onErrorHandleWith(ex => ConsStream.now(f(ex)))

  /** Recovers from potential errors by mapping them to
    * a final element using the provided function.
    */
  final def onErrorRecover[B >: A](pf: PartialFunction[Throwable, B]): ConsStream[B,F] =
    onErrorHandleWith {
      case ex if pf.isDefinedAt(ex) => ConsStream.now(pf(ex))
      case other => ConsStream.error(other)
    }

  /** Drops the first `n` elements, from left to right. */
  final def drop(n: Int): ConsStream[A,F] =
    if (n <= 0) this else this match {
      case Wait(next) => Wait[A,F](F.map(next)(_.drop(n)))
      case empty @ Empty() => empty
      case error @ Error(_) => error
      case Next(a, next) => Wait[A,F](F.map(next)(_.drop(n-1)))
      case NextSeq(list, rest) =>
        val length = list.length
        if (length == n)
          Wait[A,F](rest)
        else if (length > n)
          NextSeq[A,F](list.drop(n), rest)
        else
          Wait[A,F](F.map(rest)(_.drop(n - length)))
    }

  /** Triggers memoization of the iterable on the first traversal,
    * such that results will get reused on subsequent traversals.
    */
  final def memoize: ConsStream[A,F] =
    this match {
      case Wait(next) => Wait[A,F](F.map(F.memoize(next))(_.memoize))
      case ref @ (Empty() | Error(_)) => ref
      case Next(a, rest) => Next[A,F](a, F.map(F.memoize(rest))(_.memoize))
      case NextSeq(list, rest) => NextSeq[A,F](list, F.map(F.memoize(rest))(_.memoize))
    }

  /** Creates a new evaluable that will consume the
    * source iterator and upon completion of the source it will
    * complete with `Unit`.
    */
  final def completedL: F[Unit] = {
    def loop(tail: F[ConsStream[A,F]]): F[Unit] = F.flatMap(tail) {
      case Next(elem, rest) => loop(rest)
      case NextSeq(elems, rest) => loop(rest)
      case Wait(rest) => loop(rest)
      case Empty() => F.unit
      case Error(ex) => F.error(ex)
    }

    loop(F.now(this))
  }

  /** On evaluation it consumes the stream and for each element
    * execute the given function.
    */
  final def foreachL(cb: A => Unit): F[Unit] = {
    def loop(tail: F[ConsStream[A,F]]): F[Unit] = F.flatMap(tail) {
      case Next(elem, rest) =>
        try { cb(elem); loop(rest) }
        catch { case NonFatal(ex) => F.error(ex) }

      case NextSeq(elems, rest) =>
        try { elems.foreach(cb); loop(rest) }
        catch { case NonFatal(ex) => F.error(ex) }

      case Wait(rest) => loop(F.defer(rest))
      case Empty() => F.unit
      case Error(ex) => F.error(ex)
    }

    loop(F.now(this))
  }
}

object ConsStream {
  /** Lifts a strict value into an stream */
  def now[A, F[_]](a: A)(implicit F: Evaluable[F]): ConsStream[A,F] =
    Next[A,F](a, F.now(empty[A,F]))

  /** Builder for an [[Error]] state. */
  def error[A, F[_] : Evaluable](ex: Throwable): ConsStream[A,F] = Error[F](ex)

  /** Builder for an [[Empty]] state. */
  def empty[A, F[_] : Evaluable]: ConsStream[A,F] = Empty[F]()

  /** Builder for a [[Wait]] iterator state. */
  def wait[A, F[_] : Evaluable](rest: F[ConsStream[A,F]]): ConsStream[A,F] = Wait[A,F](rest)

  /** Builds a [[Next]] iterator state. */
  def next[A, F[_] : Evaluable](head: A, rest: F[ConsStream[A,F]]): ConsStream[A,F] =
    Next[A,F](head, rest)

  /** Builds a [[Next]] iterator state. */
  def nextSeq[A, F[_] : Evaluable](headSeq: LinearSeq[A], rest: F[ConsStream[A,F]]): ConsStream[A,F] =
    NextSeq[A,F](headSeq, rest)

  /** Lifts a strict value into an stream */
  def evalAlways[A, F[_]](a: => A)(implicit F: Evaluable[F]): ConsStream[A,F] =
    Wait[A,F](F.evalAlways {
      try Next[A,F](a, F.now(Empty[F]())) catch {
        case NonFatal(ex) => Error[F](ex)
      }
    })

  /** Lifts a strict value into an stream and
    * memoizes the result for subsequent executions.
    */
  def evalOnce[A, F[_]](a: => A)(implicit F: Evaluable[F]): ConsStream[A,F] =
    Wait[A,F](F.evalOnce {
      try Next[A,F](a, F.now(Empty[F]())) catch {
        case NonFatal(ex) => Error[F](ex)
      }
    })

  /** Promote a non-strict value representing a stream
    * to a stream of the same type.
    */
  def defer[A, F[_]](fa: => ConsStream[A,F])(implicit F: Evaluable[F]): Wait[A,F] =
    Wait[A,F](F.defer(F.evalAlways(fa)))

  /** Generates a range between `from` (inclusive) and `until` (exclusive),
    * with `step` as the increment.
    */
  def range[F[_]](from: Long, until: Long, step: Long = 1L)(implicit F: Evaluable[F]): ConsStream[Long,F] = {
    def loop(cursor: Long): ConsStream[Long,F] = {
      val isInRange = (step > 0 && cursor < until) || (step < 0 && cursor > until)
      val nextCursor = cursor + step
      if (!isInRange) Empty[F]() else
        Next[Long,F](cursor, F.evalAlways(loop(nextCursor)))
    }

    Wait[Long,F](F.evalAlways(loop(from)))
  }

  /** Converts any sequence into an async iterable.
    *
    * Because the list is a linear sequence that's known
    * (but not necessarily strict), we'll just return
    * a strict state.
    */
  def fromList[A, F[_]](list: immutable.LinearSeq[A], batchSize: Int)
    (implicit F: Evaluable[F]): F[ConsStream[A,F]] =
    if (list.isEmpty) F.now(Empty()) else F.now {
      val (first, rest) = list.splitAt(batchSize)
      NextSeq[A,F](first, F.defer(fromList(rest, batchSize)))
    }

  /** Converts an iterable into an async iterator. */
  def fromIterable[A, F[_]](iterable: Iterable[A], batchSize: Int)
    (implicit F: Evaluable[F]): F[ConsStream[A,F]] =
    F.flatMap(F.now(iterable)) { iter => fromIterator(iter.iterator, batchSize) }

  /** Converts an iterable into an async iterator. */
  def fromIterable[A, F[_]](iterable: java.lang.Iterable[A], batchSize: Int)
    (implicit F: Evaluable[F]): F[ConsStream[A,F]] =
    F.flatMap(F.now(iterable)) { iter => fromIterator(iter.iterator, batchSize) }

  /** Converts a `scala.collection.Iterator` into an async iterator. */
  def fromIterator[A, F[_]](iterator: scala.collection.Iterator[A], batchSize: Int)
    (implicit F: Evaluable[F]): F[ConsStream[A,F]] =
    F.evalOnce {
      try {
        val buffer = mutable.ListBuffer.empty[A]
        var processed = 0
        while (processed < batchSize && iterator.hasNext) {
          buffer += iterator.next()
          processed += 1
        }

        if (processed == 0) Empty()
        else if (processed == 1)
          Next[A,F](buffer.head, fromIterator(iterator, batchSize))
        else
          NextSeq[A,F](buffer.toList, fromIterator(iterator, batchSize))
      } catch {
        case NonFatal(ex) =>
          Error(ex)
      }
    }

  /** Converts a `java.util.Iterator` into an async iterator. */
  def fromIterator[A, F[_]](iterator: java.util.Iterator[A], batchSize: Int)
    (implicit F: Evaluable[F]): F[ConsStream[A,F]] =
    F.evalOnce {
      try {
        val buffer = mutable.ListBuffer.empty[A]
        var processed = 0
        while (processed < batchSize && iterator.hasNext) {
          buffer += iterator.next()
          processed += 1
        }

        if (processed == 0) Empty()
        else if (processed == 1)
          Next[A,F](buffer.head, fromIterator(iterator, batchSize))
        else
          NextSeq[A,F](buffer.toList, fromIterator(iterator, batchSize))
      } catch {
        case NonFatal(ex) =>
          Error(ex)
      }
    }

  /** A state of the [[ConsStream]] representing a deferred iterator. */
  final case class Wait[A, F[_] : Evaluable](next: F[ConsStream[A,F]])
    extends ConsStream[A,F]

  /** A state of the [[ConsStream]] representing a head/tail decomposition.
    *
    * @param head is the next element to be processed
    * @param tail is the next state in the sequence
    */
  final case class Next[A, F[_] : Evaluable](head: A, tail: F[ConsStream[A,F]])
    extends ConsStream[A,F]

  /** A state of the [[ConsStream]] representing a head/tail decomposition.
    *
    * Like [[Next]] except that the head is a strict sequence
    * of elements that don't need asynchronous execution.
    * Meant for doing buffering.
    *
    * @param headSeq is a sequence of the next elements to be processed, can be empty
    * @param tail is the next state in the sequence
    */
  final case class NextSeq[A, F[_] : Evaluable](headSeq: LinearSeq[A], tail: F[ConsStream[A,F]])
    extends ConsStream[A,F]

  /** Represents an error state in the iterator.
    *
    * This is a final state. When this state is received, the data-source
    * should have been canceled already.
    *
    * @param ex is an error that was thrown.
    */
  final case class Error[F[_] : Evaluable](ex: Throwable) extends ConsStream[Nothing,F]

  /** Represents an empty iterator.
    *
    * Received as a final state in the iteration process.
    * When this state is received, the data-source should have
    * been canceled already.
    */
  final case class Empty[F[_] : Evaluable]() extends ConsStream[Nothing,F]
}