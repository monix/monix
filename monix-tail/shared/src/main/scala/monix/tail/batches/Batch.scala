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

import scala.reflect.ClassTag

/** The `Batch` is a [[BatchCursor]] factory, similar in spirit
  * with Scala's [[scala.collection.Iterable Iterable]].
  *
  * Its [[Batch#cursor cursor()]] method can be called
  * repeatedly to yield the same sequence.
  *
  * This class is provided as an alternative to Scala's
  * [[scala.collection.Iterable Iterable]] because:
  *
  *  - the list of supported operations is smaller
  *  - implementations specialized for primitives are provided
  *    to avoid boxing
  *  - it's a factory of [[BatchCursor]], which provides hints
  *    for `recommendedBatchSize`, meaning how many batch can
  *    be processed in a batches
  *
  * Used in the [[Iterant]] implementation.
  */
abstract class Batch[+A] extends Serializable {
  def cursor(): BatchCursor[A]

  /** Creates a new generator that will only return the first `n`
    * elements of the source.
    */
  def take(n: Int): Batch[A]

  /** Creates a new generator from the source, with the first
    * `n` elements dropped, of if `n` is higher than the length
    * of the underlying collection, the it mirrors the source,
    * whichever applies.
    */
  def drop(n: Int): Batch[A]

  /** Creates a new generator emitting an interval of the values
    * produced by the source.
    *
    *  @param from the index of the first generated element
    *         which forms part of the slice.
    *  @param until the index of the first element
    *         following the slice.
    *  @return a generator which emits the element of the source
    *          past the first `from` elements using `drop`,
    *          and then takes `until - from` elements,
    *          using `take`
    */
  def slice(from: Int, until: Int): Batch[A]

  /** Creates a new generator that maps all values produced by the source
    * to new values using a transformation function.
    *
    * @param f is the transformation function
    * @return a new generator which transforms every value produced by
    *         the source by applying the function `f` to it.
    */
  def map[B](f: A => B): Batch[B]

  /** Returns a generator over all the elements of the source
    * that satisfy the predicate `p`. The order of the elements
    * is preserved.
    *
    * @param p the predicate used to test values.
    * @return a generator which produces those values of the
    *         source which satisfy the predicate `p`.
    */
  def filter(p: A => Boolean): Batch[A]

  /** Creates a generator by transforming values produced by the source
    * with a partial function, dropping those values for which the partial
    * function is not defined.
    *
    * @param pf the partial function which filters and maps the generator.
    * @return a new generator which yields each value `x` produced by this
    *         generator for which `pf` is defined
    */
  def collect[B](pf: PartialFunction[A,B]): Batch[B]

  /** Applies a binary operator to a start value and all elements
    * of this generator, going left to right.
    *
    * @param initial is the start value.
    * @param op the binary operator to apply
    * @tparam R is the result type of the binary operator.
    *
    * @return the result of inserting `op` between consecutive elements
    *         of this generator, going left to right with the start value
    *         `initial` on the left. Returns `initial` if the generator
    *         is empty.
    */
  def foldLeft[R](initial: R)(op: (R,A) => R): R

  /** Converts this generator into a Scala immutable `List`. */
  def toList: List[A] = cursor().toList

  /** Converts this generator into a standard `Array`. */
  def toArray[B >: A : ClassTag]: Array[B] = cursor().toArray

  /** Converts this generator into a Scala `Iterable`. */
  def toIterable: Iterable[A] =
    new Iterable[A] { def iterator: Iterator[A] = cursor().toIterator }
}

/** [[Batch]] builders.
  *
  * @define fromAnyArrayDesc Builds an [[ArrayBatch]] instance
  *         from any array of boxed values.
  *
  *         This will have lower performance than working with
  *         [[Batch.fromArray[A](array:Array[A])* Batch.fromArray]],
  *         since the values are boxed, however there is no
  *         requirement for a [[scala.reflect.ClassTag ClassTag]] and
  *         thus it can be used in any generic context.
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
object Batch {
  /** Given a list of elements, builds an array-backed [[Batch]] out of it. */
  def apply[A](elems: A*): Batch[A] = {
    val array = elems.asInstanceOf[Seq[AnyRef]].toArray
    fromArray(array).asInstanceOf[Batch[A]]
  }

  /** Builds a [[Batch]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array $paramArray
    */
  def fromArray[A : ClassTag](array: Array[A]): ArrayBatch[A] =
    fromArray(array, 0, array.length)

  /** Builds a [[Batch]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def fromArray[A : ClassTag](array: Array[A], offset: Int, length: Int): ArrayBatch[A] =
    new ArrayBatch[A](array, offset, length)

  /** $fromAnyArrayDesc
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def fromAnyArray[A](array: Array[_], offset: Int, length: Int): ArrayBatch[A] = {
    val ref = new ArrayBatch[Any](array.asInstanceOf[Array[Any]], offset, length, arrayAnyBuilder)
    ref.asInstanceOf[ArrayBatch[A]]
  }

  /** $fromAnyArrayDesc
    *
    * @param array $paramArray
    */
  def fromAnyArray[A](array: Array[_]): ArrayBatch[A] =
    fromAnyArray(array, 0, array.length)

  /** Converts a Scala [[scala.collection.Iterable Iterable]] into a [[Batch]]. */
  def fromIterable[A](iter: Iterable[A]): Batch[A] =
    fromIterable(iter, defaultBatchSize)

  /** Converts a Scala [[scala.collection.Iterable Iterable]]
    * into a [[Batch]].
    *
    * @param recommendedBatchSize specifies the
    *        [[BatchCursor.recommendedBatchSize]] for the generated `BatchCursor` instances
    *        of this `Batch`, specifying the batch size when doing eager processing.
    */
  def fromIterable[A](iter: Iterable[A], recommendedBatchSize: Int): Batch[A] =
    new GenericBatch[A] {
      def cursor(): BatchCursor[A] =
        BatchCursor.fromIterator(iter.iterator, recommendedBatchSize)
    }

  /** Builds a [[Batch]] from a Scala `Seq`, with lazy
    * semantics on transformations.
    */
  def fromSeq[A](seq: Seq[A]): Batch[A] = {
    val bs = if (seq.hasDefiniteSize) defaultBatchSize else 1
    fromSeq(seq, bs)
  }

  /** Builds a [[Batch]] from a Scala `Seq`, with lazy
    * semantics on transformations.
    */
  def fromSeq[A](seq: Seq[A], recommendedBatchSize: Int): Batch[A] =
    new SeqBatch(seq, recommendedBatchSize)

  /** Builds a [[Batch]] from a Scala `IndexedSeq`, with strict
    * semantics on transformations.
    */
  def fromIndexedSeq[A](seq: IndexedSeq[A]): Batch[A] = {
    val ref = seq.asInstanceOf[IndexedSeq[AnyRef]].toArray
    fromArray(ref).asInstanceOf[Batch[A]]
  }

  /** Returns an empty generator instance. */
  def empty[A]: Batch[A] = EmptyBatch

  /** Returns a [[Batch]] specialized for `Boolean`.
    *
    * @param array $paramArray
    */
  def booleans(array: Array[Boolean]): BooleansBatch =
    booleans(array, 0, array.length)

  /** Returns a [[Batch]] specialized for `Boolean`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def booleans(array: Array[Boolean], offset: Int, length: Int): BooleansBatch =
    new BooleansBatch(new ArrayBatch(array, offset, length))

  /** Returns a [[Batch]] specialized for `Byte`.
    *
    * @param array $paramArray
    */
  def bytes(array: Array[Byte]): BytesBatch =
    bytes(array, 0, array.length)

  /** Returns a [[Batch]] specialized for `Byte`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def bytes(array: Array[Byte], offset: Int, length: Int): BytesBatch =
    new BytesBatch(new ArrayBatch(array, offset, length))

  /** Returns a [[Batch]] specialized for `Char`.
    *
    * @param array $paramArray
    */
  def chars(array: Array[Char]): CharsBatch =
    chars(array, 0, array.length)

  /** Returns a [[Batch]] specialized for `Char`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def chars(array: Array[Char], offset: Int, length: Int): CharsBatch =
    new CharsBatch(new ArrayBatch(array, offset, length))

  /** Returns a [[Batch]] specialized for `Int`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def integers(array: Array[Int], offset: Int, length: Int): IntegersBatch =
    new IntegersBatch(new ArrayBatch(array, offset, length))

  /** Returns a [[Batch]] specialized for `Int`.
    *
    * @param array $paramArray
    */
  def integers(array: Array[Int]): IntegersBatch =
    integers(array, 0, array.length)

  /** Returns a [[Batch]] specialized for `Long`.
    *
    * @param array $paramArray
    */
  def longs(array: Array[Long]): LongsBatch =
    longs(array, 0, array.length)

  /** Returns a [[Batch]] specialized for `Long`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def longs(array: Array[Long], offset: Int, length: Int): LongsBatch =
    new LongsBatch(new ArrayBatch(array, offset, length))

  /** Returns a [[Batch]] specialized for `Double`.
    *
    * @param array $paramArray
    */
  def doubles(array: Array[Double]): DoublesBatch =
    doubles(array, 0, array.length)

  /** Returns a [[Batch]] specialized for `Double`.
    *
    * @param array $paramArray
    * @param offset $paramArrayOffset
    * @param length $paramArrayLength
    */
  def doubles(array: Array[Double], offset: Int, length: Int): DoublesBatch =
    new DoublesBatch(new ArrayBatch(array, offset, length))

  /** A generator producing equally spaced values in some integer interval.
    *
    * @param from the start value of the generator
    * @param until the end value of the generator (the first value NOT returned)
    * @param step the increment value of the generator (must be positive or negative)
    * @return the generator producing values `from, from + step, ...` up to, but excluding `end`
    */
  def range(from: Int, until: Int, step: Int = 1): Batch[Int] =
    new GenericBatch[Int] {
      def cursor(): BatchCursor[Int] = BatchCursor.range(from, until, step)
    }
}