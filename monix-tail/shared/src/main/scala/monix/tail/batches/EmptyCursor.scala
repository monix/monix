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

/** [[BatchCursor]] implementation that's always empty. */
object EmptyCursor extends BatchCursor[Nothing] {
  override def recommendedBatchSize: Int = 1
  override def hasNext(): Boolean = false
  override def next(): Nothing =
    throw new NoSuchElementException("next()")

  override def toIterator: Iterator[Nothing] =
    Iterator.empty

  override final def take(n: Int): BatchCursor[Nothing] = this
  override final def drop(n: Int): BatchCursor[Nothing] = this
  override final def map[B](f: (Nothing) => B): BatchCursor[B] = this
  override final def filter(p: (Nothing) => Boolean): BatchCursor[Nothing] = this
  override final def collect[B](pf: PartialFunction[Nothing, B]): BatchCursor[B] = this
  override final def slice(from: Int, until: Int): BatchCursor[Nothing] = this
}