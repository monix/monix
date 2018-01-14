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
package batches

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.reflect.ClassTag

/** Similar to Java's and Scala's `Iterator`, the `BatchCursor` type can
  * can be used to iterate over the data in a collection, but it cannot
  * be used to modify the underlying collection.
  *
  * Inspired by the standard `Iterator`, provides a way to efficiently
  * apply operations such as `map`, `filter`, `collect` on the underlying
  * collection without such operations having necessarily lazy behavior.
  * So in other words, when wrapping a standard `Array`, an application of `map`
  * will copy the data to a new `Array` instance with its elements
  * modified, immediately and is thus having strict (eager) behavior.
  * In other cases, when wrapping potentially infinite collections, like
  * `Iterable` or `Stream`, that's when lazy behavior happens.
  *
  * Sample:
  * {{{
  *   try while (cursor.hasNext()) {
  *     println(cursor.next())
  *   }
  *   catch {
  *     case NonFatal(ex) => report(ex)
  *   }
  * }}}
  *
  * This class is provided as an alternative to Scala's
  * [[scala.collection.Iterator Iterator]] because:
  *
  *  - the list of supported operations is smaller
  *  - implementations specialized for primitives are provided
  *    to avoid boxing
  *  - depending on the implementation, the behaviour of operators
  *    can be eager (e.g. `map`, `filter`), but only in case the
  *    source cursor doesn't need to be consumed (if the cursor is
  *    backed by an array, then a new array gets created, etc.)
  *  - the `recommendedBatchSize` can signal how many batch
  *    can be processed in batches
  *
  * Used in the [[Iterant]] implementation.
  *
  * @define strictOrLazyNote NOTE: application of this function can be
  *         either strict or lazy (depending on the underlying cursor type),
  *         but it does not modify the original collection.
  */
abstract class BatchCursor[+A] extends Serializable {
  /** Tests whether this cursor can provide another element.
    *
    * This method can be side-effecting, depending on the
    * implementation and thus it can also throw exceptions.
    * This is because in certain cases the only way to know
    * if there is a next element or not involves triggering
    * dangerous side-effects.
    *
    * This method is idempotent until the call to [[next]]
    * happens, meaning that multiple `hasNext` calls can be
    * made and implementations are advised to memoize the
    * result.
    *
    * @return `true` if a subsequent call to [[next]] will yield
    *         an element, `false` otherwise.
    */
  def hasNext(): Boolean

  /** Produces the next element of this iterator.
    *
    * This method is side-effecting, as it mutates the internal
    * state of the cursor and can throw exceptions.
    *
    * @return the next element of this iterator, if `hasNext` is `true`,
    *         undefined behavior otherwise (can throw exceptions).
    */
  def next(): A

  /** In case this cursor is going to be processed eagerly,
    * in batches then this value should be the recommended
    * batch size for the source cursor.
    *
    * Examples:
    *
    *  - if this cursor is iterating over a standard
    *    collection with a finite size, it can be something
    *    generous like `1024`
    *  - if it's iterating over a cheap infinite iterator
    *    (e.g. `Iterator.range`), it could be 128.
    *  - if it does any sort of I/O or blocking of threads,
    *    then the recommended value is `1`.
    *
    * Basically the batch size should be adjusted according
    * to how expensive processing this cursor is. If it's
    * a strict collection of a finite size, then it can probably
    * be processed all at once. But if it's a big database
    * result set that can block threads on reads, then it's
    * probably wise to do it one item at a time.
    */
  def recommendedBatchSize: Int

  /** Returns `true` in case our cursor is empty or `false` if there
    * are more elements to process.
    *
    * Alias for `!cursor.hasNext()`.
    */
  def isEmpty: Boolean = !hasNext()

  /** Returns `true` in case our cursor has more elements
    * to process or `false` if the cursor is empty.
    *
    * Alias for [[hasNext]].
    */
  def nonEmpty: Boolean = hasNext()

  /** Creates a new cursor that will only emit the
    * first `n` values of this cursor.
    *
    * @param  n is the number of values to take
    * @return a cursor producing only of the first `n` values of
    *         this cursor, or else the whole sequence,
    *         if it produces fewer than `n` values.
    */
  def take(n: Int): BatchCursor[A]

  /** Creates a new cursor that advances this cursor past the
    * first `n` elements, or the length of the cursor,
    * whichever is smaller.
    *
    * @param n the number of elements to drop
    * @return a cursor which produces all values of the current cursor,
    *         except it omits the first `n` values.
    */
  def drop(n: Int): BatchCursor[A]

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
  def slice(from: Int, until: Int): BatchCursor[A]

  /** Creates a new cursor that maps all produced values of this cursor
    * to new values using a transformation function.
    *
    * $strictOrLazyNote
    *
    * @param f is the transformation function
    * @return a new cursor which transforms every value produced by this
    *         cursor by applying the function `f` to it.
    */
  def map[B](f: A => B): BatchCursor[B]

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
  def filter(p: A => Boolean): BatchCursor[A]

  /** Creates a cursor by transforming values produced by the source
    * cursor with a partial function, dropping those values for which
    * the partial function is not defined.
    *
    * $strictOrLazyNote
    *
    * @param pf the partial function which filters and maps the cursor.
    * @return a new cursor which yields each value `x` produced by this
    *         cursor for which `pf` is defined
    */
  def collect[B](pf: PartialFunction[A,B]): BatchCursor[B]

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
    while (hasNext()) result = op(result, next())
    result
  }

  /** Converts this cursor into a Scala immutable `List`,
    * consuming it in the process.
    */
  def toList: List[A] = {
    val buffer = ListBuffer.empty[A]
    while (hasNext()) buffer += next()
    buffer.toList
  }

  /** Converts this cursor into an `Array`,
    * consuming it in the process.
    */
  def toArray[B >: A : ClassTag]: Array[B] = {
    val buffer = ArrayBuffer.empty[B]
    while (hasNext()) buffer += next()
    buffer.toArray
  }

  /** Converts this cursor into a reusable array-backed [[Batch]],
    * consuming it in the process.
    */
  def toGenerator: Batch[A] = {
    val array = asInstanceOf[BatchCursor[AnyRef]].toArray
    Batch.fromArray(array).asInstanceOf[Batch[A]]
  }

  /** Converts this cursor into a Scala `Iterator`. */
  def toIterator: Iterator[A]
}

/** [[BatchCursor]] builders.
  *
  * @define fromAnyArrayDesc Builds an [[ArrayCursor]] instance
  *         from any array of boxed values.
  *
  *         This will have lower performance than working with
  *         [[BatchCursor.fromArray[A](array:Array[A])* BatchCursor.fromArray]],
  *         since the values are boxed, however there is no requirement for a
  *         [[scala.reflect.ClassTag ClassTag]] and thus it can
  *         be used in any generic context.
  *
  * @define paramArray is the underlying reference to use for traversing
  *         and transformations
  *
  * @define paramArrayOffset is the offset to start from, which would have
  *         been zero by default
  *
  * @define paramArrayLength is the length of created cursor, which would
  *         have been `array.length` by default
  */
object BatchCursor {
  /** Given a list of cursor, builds an array-backed [[BatchCursor]] out of it. */
  def apply[A](elems: A*): BatchCursor[A] = {
    val array = elems.asInstanceOf[Seq[AnyRef]].toArray
    fromArray(array).asInstanceOf[BatchCursor[A]]
  }

  /** Converts a Scala [[scala.collection.Iterator]] into a [[BatchCursor]].
    *
    * @param iter is the [[scala.collection.Iterator Iterator]]
    *        to wrap in a `BatchCursor` instance
    */
  def fromIterator[A](iter: Iterator[A]): BatchCursor[A] = {
    val bs = if (iter.hasDefiniteSize) defaultBatchSize else 1
    new IteratorCursor[A](iter, bs)
  }

  /** Converts a Scala [[scala.collection.Iterator]] into a [[BatchCursor]].
    *
    * @param iter is the [[scala.collection.Iterator Iterator]]
    *        to wrap in a `BatchCursor` instance
    *
    * @param recommendedBatchSize specifies the
    *        [[BatchCursor.recommendedBatchSize]] for the resulting
    *        `BatchCursor` instance, specifying the batch size when
    *        doing eager processing.
    */
  def fromIterator[A](iter: Iterator[A], recommendedBatchSize: Int): BatchCursor[A] =
    new IteratorCursor[A](iter, recommendedBatchSize)

  /** Builds a [[BatchCursor]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array $paramArray
    */
  def fromArray[A : ClassTag](array: Array[A]): ArrayCursor[A] =
    new ArrayCursor[A](array)

  /** Builds a [[BatchCursor]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def fromArray[A : ClassTag](array: Array[A], offset: Int, length: Int): ArrayCursor[A] =
    new ArrayCursor[A](array, offset, length)

  /** $fromAnyArrayDesc
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def fromAnyArray[A](array: Array[_], offset: Int, length: Int): ArrayCursor[A] = {
    val ref = new ArrayCursor[Any](array.asInstanceOf[Array[Any]], offset, length, arrayAnyBuilder)
    ref.asInstanceOf[ArrayCursor[A]]
  }

  /** $fromAnyArrayDesc
    *
    * @param array $paramArray
    */
  def fromAnyArray[A](array: Array[_]): ArrayCursor[A] =
    fromAnyArray(array, 0, array.length)

  /** Builds a [[BatchCursor]] from a Scala `Seq`, with lazy
    * semantics on transformations.
    */
  def fromSeq[A](seq: Seq[A]): BatchCursor[A] = {
    val bs = if (seq.hasDefiniteSize) defaultBatchSize else 1
    fromSeq(seq, bs)
  }

  /** Builds a [[BatchCursor]] from a Scala `Seq`, with lazy
    * semantics on transformations.
    */
  def fromSeq[A](seq: Seq[A], recommendedBatchSize: Int): BatchCursor[A] =
    fromIterator(seq.iterator, recommendedBatchSize)

  /** Builds a [[BatchCursor]] from a Scala `IndexedSeq`, with strict
    * semantics on transformations.
    */
  def fromIndexedSeq[A](seq: IndexedSeq[A]): BatchCursor[A] = {
    val ref = seq.asInstanceOf[IndexedSeq[AnyRef]].toArray
    fromArray(ref).asInstanceOf[BatchCursor[A]]
  }

  /** Returns a generic, empty cursor instance. */
  def empty[A]: BatchCursor[A] = EmptyCursor

  /** Returns a [[BatchCursor]] specialized for `Boolean`.
    *
    * @param array $paramArray
    */
  def booleans(array: Array[Boolean]): BooleansCursor =
    booleans(array, 0, array.length)

  /** Returns a [[BatchCursor]] specialized for `Boolean`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def booleans(array: Array[Boolean], offset: Int, length: Int): BooleansCursor =
    new BooleansCursor(array, offset, length)

  /** Returns a [[BatchCursor]] specialized for `Byte`.
    *
    * @param array $paramArray
    */
  def bytes(array: Array[Byte]): BytesCursor =
    bytes(array, 0, array.length)

  /** Returns a [[BatchCursor]] specialized for `Byte`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def bytes(array: Array[Byte], offset: Int, length: Int): BytesCursor =
    new BytesCursor(array, offset, length)

  /** Returns a [[BatchCursor]] specialized for `Char`.
    *
    * @param array $paramArray
    */
  def chars(array: Array[Char]): CharsCursor =
    chars(array, 0, array.length)

  /** Returns a [[BatchCursor]] specialized for `Char`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def chars(array: Array[Char], offset: Int, length: Int): CharsCursor =
    new CharsCursor(array, offset, length)

  /** Returns a [[BatchCursor]] specialized for `Int`.
    *
    * @param array $paramArray
    */
  def integers(array: Array[Int]): IntegersCursor =
    integers(array, 0, array.length)

  /** Returns a [[BatchCursor]] specialized for `Int`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def integers(array: Array[Int], offset: Int, length: Int): IntegersCursor =
    new IntegersCursor(array, offset, length)

  /** Returns a [[BatchCursor]] specialized for `Long`.
    *
    * @param array $paramArray
    */
  def longs(array: Array[Long]): LongsCursor =
    longs(array, 0, array.length)

  /** Returns a [[BatchCursor]] specialized for `Long`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def longs(array: Array[Long], offset: Int, length: Int): LongsCursor =
    new LongsCursor(array, offset, length)

  /** Returns a [[BatchCursor]] specialized for `Double`.
    *
    * @param array $paramArray
    */
  def doubles(array: Array[Double]): DoublesCursor =
    doubles(array, 0, array.length)

  /** Returns a [[BatchCursor]] specialized for `Double`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def doubles(array: Array[Double], offset: Int, length: Int): DoublesCursor =
    new DoublesCursor(array, offset, length)

  /** A cursor producing equally spaced values in some integer interval.
    *
    * @param from the start value of the cursor
    * @param until the end value of the cursor (the first value NOT returned)
    * @param step the increment value of the cursor (must be positive or negative)
    * @return the cursor producing values `from, from + step, ...` up to, but excluding `end`
    */
  def range(from: Int, until: Int, step: Int = 1): BatchCursor[Int] =
    BatchCursor.fromIterator(Iterator.range(from, until, step), defaultBatchSize)

  /** Creates an infinite-length iterator returning the results of evaluating
    * an expression. The expression is recomputed for every element.
    *
    *  @param f the computation to repeatedly evaluate
    *  @return the iterator containing an infinite number of results of evaluating `f`
    */
  def continually[A](f: => A): BatchCursor[A] =
    fromIterator(Iterator.continually(f), 1)
}