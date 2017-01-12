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

import monix.tail.cursors.{ArrayCursor, EmptyCursor, Generator, IteratorCursor}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.reflect.ClassTag

/** Similar to Java's and Scala's `Iterator`, the `Cursor` type can
  * can be used to iterate over the data in a collection, but it cannot
  * be used to modify the underlying collection.
  *
  * Inspired by the standard `Iterator`, but also by the C# `IEnumerator`,
  * it exists because [[Iterant]] needs a way to efficiently apply
  * operations such as `map`, `filter`, `collect` on the underlying
  * collection without such operations having necessarily lazy behavior.
  * So in other words, when wrapping a standard `Array`, an application of
  * `map` will copy the data to a new `Array` instance with its elements
  * modified, immediately and is thus having strict behavior. In other cases,
  * when wrapping potentially infinite collections, like `Iterable` or `Stream`,
  * that's when lazy behavior happens.
  *
  * Sample:
  * {{{
  *   try while (cursor.moveNext()) {
  *     println(cursor.current)
  *   }
  *   catch {
  *     case NonFatal(ex) => report(ex)
  *   }
  * }}}
  *
  * Contract:
  *
  *  - the [[monix.tail.Cursor#current current]] method does not trigger
  *    any side-effects, it simply caches the last generated value and can
  *    be called multiple times (in contrast with `Iterator.next()`);
  *    it should also not throw any exceptions, unless `moveNext()` never
  *    happened so there is no `current` element to return
  *
  *  - in order to advance the cursor to the next element, one has to call
  *    the [[monix.tail.Cursor#moveNext moveNext()]] method; this
  *    method triggers any possible side-effects, returns `true` for as
  *    long as there are elements to return and can also throw
  *    exceptions, in which case the iteration must stop
  *
  * Provided because it is needed by the [[Iterant]] type, but exposed
  * as a public type, so can be used by users.
  *
  * @define strictOrLazyNote NOTE: application of this function can be
  *         either strict or lazy (depending on the underlying cursor type),
  *         but it does not modify the original collection.
  */
trait Cursor[+A] extends Serializable {
  /** Gets the current element of the underlying collection.
    *
    * After an enumerator is created, the [[moveNext]] method must be
    * called to advance the cursor to the first element of the collection,
    * before reading the value of the [[current]] property; otherwise the
    * behavior of `current` is undefined and can throw an exception.
    *
    * This method does not move the position of the cursor, and consecutive
    * calls to [[current]] return the same object until [[moveNext]] is called.
    */
  def current: A

  /** Advances the enumerator to the next element of the collection,
    * when available, otherwise returns `false`.
    *
    * A cursor remains valid as long as the collection remains unchanged.
    * If changes are made to the collection, such as adding, modifying,
    * or deleting elements, the cursor might be invalidated and the next
    * call to [[moveNext]] might throw an exception
    * (such as `ConcurrentModificationException`), but not necessarily.
    *
    * @return `true` in case the advancement succeeded and there is a
    *        [[current]] element that can be fetched, or `false` in
    *        case there are no further elements and the iteration must
    *        stop
    */
  def moveNext(): Boolean

  /** Returns `true` if the cursor can be advanced or `false` otherwise.
    *
    * This method does not advance our cursor, users still have to call
    * [[moveNext]], this method being useful for optimizations,
    * for knowing in advance if the cursor is empty or not.
    *
    * This method can be side-effecting, even if it doesn't move our
    * cursor (i.e. in some cases we can't know if there are more
    * elements to process without producing an irreversible effect).
    *
    * @return `true` in case we have more elements to process and the
    *        cursor can be advanced (the next call to `moveNext` will
    *        return `true`), or `false` otherwise.
    */
  def hasMore(): Boolean

  /** Returns `true` in case our cursor is empty or `false` if there
    * are more elements to process.
    *
    * Alias for `!cursor.hasMore()`.
    */
  def isEmpty: Boolean = !hasMore()

  /** Returns `true` in case our cursor has more elements
    * to process or `false` if the cursor is empty.
    *
    * Alias for [[hasMore]].
    */
  def nonEmpty: Boolean = hasMore()

  /** Selects first `n` values of this cursor.
    *
    * @param  n is the number of values to take
    * @return a cursor producing only of the first `n` values of
    *         this cursor, or else the whole sequence,
    *         if it produces fewer than `n` values.
    */
  def take(n: Int): Cursor[A]

  /** Advances this cursor past the first `n` elements,
    * or the length of the cursor, whichever is smaller.
    *
    * @param n the number of elements to drop
    * @return a cursor which produces all values of the current cursor,
    *         except it omits the first `n` values.
    */
  def drop(n: Int): Cursor[A]

  /** Creates an cursor returning an interval of the values
    * produced by this cursor.
    *
    *  @param from the index of the first element in this cursor
    *         which forms part of the slice.
    *  @param until the index of the first element
    *         following the slice.
    *  @return a cursor which advances this cursor past
    *          the first `from` elements using `drop`,
    *          and then takes `until - from` elements,
    *          using `take`
    */
  def slice(from: Int, until: Int): Cursor[A]

  /** Creates a new cursor that maps all produced values of this cursor
    * to new values using a transformation function.
    *
    * $strictOrLazyNote
    *
    * @param f is the transformation function
    * @return a new cursor which transforms every value produced by this
    *         cursor by applying the function `f` to it.
    */
  def map[B](f: A => B): Cursor[B]

  /** Returns an cursor over all the elements of the source cursor
    * that satisfy the predicate `p`. The order of the elements
    * is preserved.
    *
    * $strictOrLazyNote
    *
    * @param p the predicate used to test values.
    * @return a cursor which produces those values of this cursor
    *         which satisfy the predicate `p`.
    */
  def filter(p: A => Boolean): Cursor[A]

  /** Creates a cursor by transforming values produced by the source
    * cursor with a partial function, dropping those values for which
    * the partial function is not defined.
    *
    * $strictOrLazyNote
    *
    * @param pf the partial function which filters and maps the cursor.
    * @return a new cursor which yields each value `x` produced by this
    *         cursor for which `pf` is defined the image `pf(x)`.
    */
  def collect[B](pf: PartialFunction[A,B]): Cursor[B]

  /** Applies a binary operator to a start value and all elements
    * of this cursor, going left to right.
    *
    * NOTE: applying this function on the cursor will consume it
    * completely.
    *
    * @param initial is the start value.
    * @param op the binary operator to apply
    * @tparam R is the result type of the binary operator.
    *
    * @return the result of inserting `op` between consecutive elements
    *         of this cursor, going left to right with the start value
    *         `initial` on the left. Returns `initial` if the cursor
    *         is empty.
    */
  def foldLeft[R](initial: R)(op: (R,A) => R): R = {
    var result = initial
    while (moveNext()) result = op(result, current)
    result
  }

  /** Converts this cursor into a Scala immutable `List`,
    * consuming it in the process.
    */
  def toList: List[A] = {
    val buffer = ListBuffer.empty[A]
    while (moveNext()) buffer += current
    buffer.toList
  }

  /** Converts this cursor into an `Array`,
    * consuming it in the process.
    */
  def toArray[B >: A : ClassTag]: Array[B] = {
    val buffer = ArrayBuffer.empty[A]
    while (moveNext()) buffer += current
    buffer.toArray
  }

  /** Converts this cursor into a reusable array-backed
    * [[monix.tail.cursors.Generator Generator]],
    * consuming it in the process.
    */
  def toGenerator: Generator[A] = {
    val array = asInstanceOf[Cursor[AnyRef]].toArray
    Generator.fromArray(array).asInstanceOf[Generator[A]]
  }

  /** Converts this cursor into a Scala `Iterator`. */
  def toIterator: Iterator[A]
}

object Cursor {
  /** Given a list of cursor, builds an array-backed [[Cursor]] out of it. */
  def apply[A](elems: A*): Cursor[A] = {
    val array = elems.asInstanceOf[Seq[AnyRef]].toArray
    fromArray(array).asInstanceOf[Cursor[A]]
  }

  /** Converts a Scala `Iterator` into a `Cursor`. */
  def fromIterator[A](iter: Iterator[A]): Cursor[A] =
    new IteratorCursor[A](iter)

  /** Builds a [[Cursor]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array is the underlying reference to use for traversing
    *        and transformations
    */
  def fromArray[A](array: Array[A]): Cursor[A] =
    new ArrayCursor[A](array)

  /** Builds a [[Cursor]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array is the underlying reference to use for traversing
    *        and transformations
    *
    * @param offset is the offset to start from, which would have
    *        been zero by default
    *
    * @param length is the length of created cursor, which would
    *        have been `array.length` by default
    */
  def fromArray[A](array: Array[A], offset: Int, length: Int): Cursor[A] =
    new ArrayCursor[A](array, offset, length)

  /** Builds a [[Cursor]] from a Scala `Seq`, with lazy
    * semantics on transformations.
    */
  def fromSeq[A](seq: Seq[A]): Cursor[A] =
    fromIterator(seq.iterator)

  /** Builds a [[Cursor]] from a Scala `IndexedSeq`, with strict
    * semantics on transformations.
    */
  def fromIndexedSeq[A](seq: IndexedSeq[A]): Cursor[A] = {
    val ref = seq.asInstanceOf[IndexedSeq[AnyRef]].toArray
    fromArray(ref).asInstanceOf[Cursor[A]]
  }

  /** Returns a generic, empty cursor instance. */
  def empty[A]: Cursor[A] = EmptyCursor

  /** A cursor producing equally spaced values in some integer interval.
    *
    * @param from the start value of the cursor
    * @param until the end value of the cursor (the first value NOT returned)
    * @param step the increment value of the cursor (must be positive or negative)
    * @return the cursor producing values `from, from + step, ...` up to, but excluding `end`
    */
  def range(from: Int, until: Int, step: Int = 1): Cursor[Int] =
    Cursor.fromIterator(Iterator.range(from, until, step))
}
