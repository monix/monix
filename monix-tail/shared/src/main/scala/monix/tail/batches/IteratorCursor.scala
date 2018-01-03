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

/** [[BatchCursor]] type that works over an
  * underlying `Iterator`.
  *
  * NOTE: all transformations are delegated to the underlying
  * `Iterator` and may thus have lazy behavior.
  */
final class IteratorCursor[+A](
  underlying: Iterator[A],
  override val recommendedBatchSize: Int)
  extends BatchCursor[A] {

  override def hasNext(): Boolean =
    underlying.hasNext
  override def next(): A =
    underlying.next()

  override def take(n: Int): BatchCursor[A] =
    new IteratorCursor[A](underlying.take(n), recommendedBatchSize)

  override def drop(n: Int): BatchCursor[A] =
    new IteratorCursor[A](underlying.drop(n), recommendedBatchSize)

  override def slice(from: Int, until: Int): BatchCursor[A] =
    new IteratorCursor[A](underlying.slice(from, until), recommendedBatchSize)

  override def map[B](f: (A) => B): BatchCursor[B] =
    new IteratorCursor(underlying.map(f), recommendedBatchSize)

  override def filter(p: (A) => Boolean): BatchCursor[A] =
    new IteratorCursor(underlying.filter(p), recommendedBatchSize)

  override def collect[B](pf: PartialFunction[A, B]): BatchCursor[B] =
    new IteratorCursor(underlying.collect(pf), recommendedBatchSize)

  override def toIterator: Iterator[A] =
    underlying
}