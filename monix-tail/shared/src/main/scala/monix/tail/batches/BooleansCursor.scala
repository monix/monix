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

/** [[BatchCursor]] implementation specialized for `Boolean`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayCursor ArrayCursor]]
  * implementation, which is `@specialized`. Using `BooleansCursor`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class BooleansCursor(underlying: ArrayCursor[Boolean]) extends BatchCursor[Boolean] {
  def this(array: Array[Boolean]) =
    this(new ArrayCursor(array))
  def this(array: Array[Boolean], offset: Int, length: Int) =
    this(new ArrayCursor(array, offset, length))

  override def hasNext(): Boolean = underlying.hasNext()
  override def next(): Boolean = underlying.next()

  override def recommendedBatchSize: Int = underlying.recommendedBatchSize
  override def toIterator: Iterator[Boolean] = underlying.toIterator

  override def map[B](f: Boolean => B): ArrayCursor[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Boolean, B]): ArrayCursor[B] =
    underlying.collect(pf)

  override def take(n: Int): BooleansCursor =
    new BooleansCursor(underlying.take(n))
  override def drop(n: Int): BooleansCursor =
    new BooleansCursor(underlying.drop(n))
  override def slice(from: Int, until: Int): BooleansCursor =
    new BooleansCursor(underlying.slice(from, until))
  override def filter(p: (Boolean) => Boolean): BooleansCursor =
    new BooleansCursor(underlying.filter(p))
}
