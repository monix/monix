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

package monix.interact.cursors

import java.util
import monix.interact.Cursor
import monix.interact.exceptions.{CursorIsFinishedException, CursorNotStartedException}

/** [[monix.interact.Cursor Cursor]] type that works over an underlying `Array`.
  *
  * NOTE: all transformations happen by copying from the source array
  * into a modified copy, hence all transformations have strict behavior!
  */
class ArrayCursor[A](array: Array[A], offset: Int, length: Int) extends Cursor[A] { self =>
  require(length <= array.length, "specified length <= array.length")
  require(0 <= offset && offset <= length, "0 <= offset <= length")

  def this(array: Array[A]) =
    this(array, 0, array.length)

  private[this] var index: Int = -1

  override def current: A = {
    try array(index) catch {
      case ex: ArrayIndexOutOfBoundsException =>
        if (index < 0)
          throw new CursorNotStartedException
        else if (index >= length)
          throw new CursorIsFinishedException
        else
          throw ex
    }
  }

  override def moveNext(): Boolean = {
    if (index < offset) index = offset
    else if (index < length) index += 1
    index < length
  }

  override def map[B](f: (A) => B): Cursor[B] = {
    val oldOffset = if (index >= offset) index else offset
    val newLength = length - oldOffset
    val copy = new Array[AnyRef](length)

    var i = 0
    while (i < newLength) {
      copy(i) = f(array(i+oldOffset)).asInstanceOf[AnyRef]
      i += 1
    }

    new ArrayCursor[AnyRef](copy, 0, newLength)
      .asInstanceOf[Cursor[B]]
  }

  override def filter(p: (A) => Boolean): Cursor[A] = {
    val oldOffset = if (index >= offset) index else offset
    val buffer = Array.newBuilder[AnyRef]

    var i = oldOffset
    while (i < length) {
      val elem = array(i)
      if (p(elem)) buffer += array(i).asInstanceOf[AnyRef]
      i += 1
    }

    val copy = buffer.result()
    new ArrayCursor[AnyRef](copy, 0, copy.length)
      .asInstanceOf[Cursor[A]]
  }

  override def collect[B](pf: PartialFunction[A, B]): Cursor[B] = {
    val oldOffset = if (index >= offset) index else offset
    val buffer = Array.newBuilder[AnyRef]

    var i = oldOffset
    while (i < length) {
      val elem = array(i)
      if (pf.isDefinedAt(elem)) buffer += pf(array(i)).asInstanceOf[AnyRef]
      i += 1
    }

    val copy = buffer.result()
    new ArrayCursor[AnyRef](copy, 0, copy.length)
      .asInstanceOf[Cursor[B]]
  }

  override def toIterator: Iterator[A] = {
    val newOffset = if (index < offset) offset else index
    val newLength = length - newOffset
    if (newLength <= 0) Iterator.empty else {
      var ref = array.iterator
      if (newOffset > 0) ref = ref.drop(newOffset)
      if (newLength < array.length) ref = ref.take(newLength)
      ref
    }
  }

  override def toJavaIterator[B >: A]: util.Iterator[B] = {
    import scala.collection.JavaConverters._
    toIterator.asInstanceOf[Iterator[B]].asJava
  }
}