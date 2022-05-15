/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.tail.batches

/**
  * Reusable [[BatchCursor]] base class that can be used for
  * implementing cursors by just providing the primitive operations,
  * `hasNext`, `next` and `recommendedBatchSize`.
  */
abstract class GenericCursor[+A] extends BatchCursor[A] { self =>
  def take(n: Int): BatchCursor[A] = {
    if (n <= 0) BatchCursor.empty
    else
      new GenericCursor[A] {
        private[this] var taken = 0

        def hasNext(): Boolean =
          taken < n && self.hasNext()

        def next(): A = {
          taken += 1
          self.next()
        }

        def recommendedBatchSize: Int =
          self.recommendedBatchSize
      }
  }

  def drop(n: Int): BatchCursor[A] = {
    if (n <= 0) self
    else
      new GenericCursor[A] {
        private[this] var dropped = false

        def hasNext(): Boolean = {
          if (!dropped) {
            dropped = true
            var count = 0
            while (count < n) {
              if (!self.hasNext()) return false
              self.next()
              count += 1
            }
          }
          self.hasNext()
        }

        def next(): A =
          self.next()

        def recommendedBatchSize: Int =
          self.recommendedBatchSize
      }
  }

  def slice(from: Int, until: Int): BatchCursor[A] =
    drop(from).take(until - from)

  def map[B](f: A => B): BatchCursor[B] =
    new GenericCursor[B] {
      def hasNext(): Boolean =
        self.hasNext()
      def next(): B =
        f(self.next())
      def recommendedBatchSize: Int =
        self.recommendedBatchSize
    }

  def filter(p: A => Boolean): BatchCursor[A] =
    new GenericCursor[A] {
      private[this] var item: A = _
      private[this] var hasItem: Boolean = false

      def hasNext(): Boolean = hasItem || {
        var continue = true
        while (continue) {
          if (!self.hasNext()) return false
          item = self.next()
          continue = !p(item)
        }
        hasItem = true
        true
      }

      def next(): A = {
        if (hasItem) {
          hasItem = false
          item
        } else {
          BatchCursor.empty.next()
        }
      }

      def recommendedBatchSize: Int =
        self.recommendedBatchSize
    }

  def collect[B](pf: PartialFunction[A, B]): BatchCursor[B] =
    new GenericCursor[B] {
      private[this] var item: A = _
      private[this] var hasItem: Boolean = false

      def hasNext(): Boolean = hasItem || {
        var continue = true
        while (continue) {
          if (!self.hasNext()) return false
          item = self.next()
          continue = !pf.isDefinedAt(item)
        }
        hasItem = true
        true
      }

      def next(): B = {
        if (hasItem) {
          hasItem = false
          pf(item)
        } else {
          BatchCursor.empty.next()
        }
      }

      def recommendedBatchSize: Int =
        self.recommendedBatchSize
    }

  def toIterator: Iterator[A] =
    new Iterator[A] {
      def hasNext: Boolean = self.hasNext()
      def next(): A = self.next()
    }
}
