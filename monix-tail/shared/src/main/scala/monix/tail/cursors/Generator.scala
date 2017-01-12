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

package monix.tail.cursors

import monix.tail.Cursor
import scala.reflect.ClassTag

/** The `Generator` is a [[Cursor]] factory, similar in spirit
  * with Scala's `Iterable`.
  *
  * It's [[Generator#cursor cursor()]] method can be called
  * repeatedly to yield the same sequence.
  */
trait Generator[+A] extends Serializable {
  def cursor(): Cursor[A]

  /** Transforms this generator with a mapping function for
    * the underlying [[Cursor]].
    *
    * NOTE: application of this function can be
    * either strict or lazy (depending on the underlying generator type),
    * but it does not modify the original collection.
    *
    *  - if this generator is an `Array`-backed generator, the given
    *    function will be applied immediately to create a new array with
    *    an accompanying generator.
    *  - if this generator is backed by `Iterable`, then the given
    *    function will be applied lazily, on demand
    *
    * @param f is the function used
    *
    * @return a new generator with its underlying sequence transformed
    *         by the mapping function
    */
  def transform[B](f: Cursor[A] => Cursor[B]): Generator[B] = {
    val self = this
    new Generator[B] { def cursor(): Cursor[B] = f(self.cursor()) }
  }

  /** Converts this cursor into a Scala immutable `List`. */
  def toList: List[A] = cursor().toList

  /** Converts this cursor into a standard `Array`. */
  def toArray[B >: A : ClassTag]: Array[B] = cursor().toArray

  /** Converts this cursor into a Scala `Iterable`. */
  def toIterable: Iterable[A] =
    new Iterable[A] { def iterator: Iterator[A] = cursor().toIterator }
}

object Generator {
  /** Given a list of cursor, builds an array-backed [[Generator]] out of it. */
  def apply[A](elems: A*): Generator[A] = {
    val array = elems.asInstanceOf[Seq[AnyRef]].toArray
    fromArray(array).asInstanceOf[Generator[A]]
  }

  /** Builds a [[Generator]] from a standard `Array` */
  def fromArray[A](array: Array[A]): Generator[A] =
    new FromArray[A](array, 0, array.length)

  /** Builds a [[Generator]] from a standard `Array` */
  def fromArray[A](array: Array[A], offset: Int, length: Int): Generator[A] =
    new FromArray[A](array, offset, length)

  /** Converts a Scala `Iterable` into a `Cursor`. */
  def fromIterable[A](iter: Iterable[A]): Generator[A] =
    new FromIterable[A](iter)

  /** Builds a [[Generator]] from a Scala `Seq`, with lazy
    * semantics on transformations.
    */
  def fromSeq[A](seq: Seq[A]): Generator[A] =
    new FromIterable[A](seq)

  /** Builds a [[Generator]] from a Scala `IndexedSeq`, with strict
    * semantics on transformations.
    */
  def fromIndexedSeq[A](seq: IndexedSeq[A]): Generator[A] = {
    val ref = seq.asInstanceOf[IndexedSeq[AnyRef]].toArray
    fromArray(ref).asInstanceOf[Generator[A]]
  }

  /** Returns an empty generator instance. */
  def empty[A]: Generator[A] = Empty

  /** A generator producing equally spaced values in some integer interval.
    *
    * @param from the start value of the generator
    * @param until the end value of the generator (the first value NOT returned)
    * @param step the increment value of the generator (must be positive or negative)
    * @return the generator producing values `from, from + step, ...` up to, but excluding `end`
    */
  def range(from: Int, until: Int, step: Int = 1): Generator[Int] =
    fromIterable(Iterable.range(from, until, step))

  // Reusable empty generator instance
  private object Empty extends Generator[Nothing] {
    override def cursor() = EmptyCursor
    override def transform[B](f: (Cursor[Nothing]) => Cursor[B]): Generator[B] = Empty
  }

  // Array-backed generator implementation with strict semantics on transform
  private final class FromArray[+A](ref: Array[A], offset: Int, length: Int)
    extends Generator[A] {

    override def cursor(): Cursor[A] =
      new ArrayCursor[A](ref, offset, length)

    override def transform[B](f: Cursor[A] => Cursor[B]): Generator[B] = {
      val array = f(cursor()).asInstanceOf[Cursor[AnyRef]].toArray
      if (array.length == 0) Empty else
        new FromArray(array, 0, array.length).asInstanceOf[Generator[B]]
    }
  }

  // Iterable-backed generator implementation with lazy semantics on transform
  private final class FromIterable[+A](ref: Iterable[A]) extends Generator[A] {
    override def cursor(): Cursor[A] =
      Cursor.fromIterator(ref.iterator)
  }
}