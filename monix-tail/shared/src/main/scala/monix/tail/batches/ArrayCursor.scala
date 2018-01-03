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

import scala.collection.mutable.ArrayBuilder
import scala.reflect.ClassTag

/** [[BatchCursor]] type that works over an underlying `Array`.
  *
  * NOTE: all transformations happen by copying from the source array
  * into a modified copy, hence all transformations have strict behavior!
  *
  * To build an instance, prefer `BatchCursor.fromArray`.
  */
final class ArrayCursor[@specialized(Boolean, Byte, Char, Int, Long, Double) A]
  (_array: Array[A], _offset: Int, _length: Int, newBuilder: () => ArrayBuilder[A])
  extends BatchCursor[A] { self =>

  require(_offset + _length <= _array.length, "offset + length <= array.length")
  require(0 <= _offset && _offset <= _array.length, "0 <= offset <= length")

  def this(array: Array[A], offset: Int, length: Int)(implicit tag: ClassTag[A]) =
    this(array, offset, length, () => ArrayBuilder.make[A]())
  def this(array: Array[A])(implicit tag: ClassTag[A]) =
    this(array, 0, array.length)

  // Int.MaxValue means that arrays can be processed whole
  override val recommendedBatchSize: Int = Int.MaxValue

  private[this] val limit = _offset + _length
  private[this] var index: Int = -1

  def array: Array[A] = _array
  def offset: Int = _offset
  def length: Int = _length

  override def hasNext(): Boolean =
    getNextIndex < limit

  override def next(): A = {
    index = getNextIndex
    array(index)
  }

  override def take(n: Int): ArrayCursor[A] = {
    val start = getNextIndex
    val newLimit = math.min(start+n, limit)
    new ArrayCursor(_array, start, newLimit-start, newBuilder)
  }

  override def drop(n: Int): ArrayCursor[A] = {
    val start = getNextIndex
    val newOffset = math.min(start+n, limit)
    val newLength = limit-newOffset
    new ArrayCursor(_array, newOffset, newLength, newBuilder)
  }

  override def slice(from: Int, until: Int): ArrayCursor[A] =
    drop(from).take(until - from)

  override def map[B](f: (A) => B): ArrayCursor[B] = {
    val oldOffset = getNextIndex
    val newLength = limit - oldOffset

    if (newLength <= 0) {
      BatchCursor.fromAnyArray[B](Array.empty, 0, 0)
    }
    else {
      val copy = new Array[AnyRef](newLength)
      var i = 0
      while (i < newLength) {
        copy(i) = f(_array(i+oldOffset)).asInstanceOf[AnyRef]
        i += 1
      }

      BatchCursor.fromAnyArray[B](copy, 0, newLength)
    }
  }

  override def filter(p: (A) => Boolean): ArrayCursor[A] = {
    val oldOffset = getNextIndex
    val buffer = newBuilder()

    var oldIndex = oldOffset
    while (oldIndex < limit) {
      val elem = _array(oldIndex)
      if (p(elem)) buffer += _array(oldIndex)
      oldIndex += 1
    }

    val copy = buffer.result()
    new ArrayCursor[A](copy, 0, copy.length, newBuilder)
  }

  override def collect[B](pf: PartialFunction[A, B]): ArrayCursor[B] = {
    val oldOffset = getNextIndex
    val buffer = ArrayBuilder.make[AnyRef]()

    var oldIndex = oldOffset
    while (oldIndex < limit) {
      val elem = _array(oldIndex)
      if (pf.isDefinedAt(elem)) buffer += pf(_array(oldIndex)).asInstanceOf[AnyRef]
      oldIndex += 1
    }

    BatchCursor.fromAnyArray[B](buffer.result())
  }

  override def toGenerator: Batch[A] = {
    val newOffset = getNextIndex
    val newLength = limit - newOffset
    new ArrayBatch[A](_array, newOffset, newLength, newBuilder)
  }

  override def toIterator: Iterator[A] = {
    val newOffset = getNextIndex
    val newLength = limit - newOffset
    if (newLength <= 0) Iterator.empty else {
      var ref = _array.iterator
      if (newOffset > 0) ref = ref.drop(newOffset)
      if (newLength < _array.length) ref = ref.take(newLength)
      ref
    }
  }

  @inline private def getNextIndex: Int =
    if (index < _offset) _offset else index+1
}