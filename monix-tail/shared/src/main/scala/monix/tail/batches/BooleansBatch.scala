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

package monix.tail
package batches

/** [[Batch]] implementation specialized for `Boolean`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayBatch ArrayBatch]]
  * implementation, which is `@specialized`. Using `BooleansBatch`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class BooleansBatch(underlying: ArrayBatch[Boolean]) extends Batch[Boolean] {

  override def cursor(): BooleansCursor =
    new BooleansCursor(underlying.cursor())

  override def take(n: Int): BooleansBatch =
    new BooleansBatch(underlying.take(n))
  override def drop(n: Int): BooleansBatch =
    new BooleansBatch(underlying.drop(n))
  override def slice(from: Int, until: Int): BooleansBatch =
    new BooleansBatch(underlying.slice(from, until))
  override def filter(p: (Boolean) => Boolean): BooleansBatch =
    new BooleansBatch(underlying.filter(p))

  override def map[B](f: (Boolean) => B): ArrayBatch[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Boolean, B]): ArrayBatch[B] =
    underlying.collect(pf)

  override def foldLeft[R](initial: R)(op: (R, Boolean) => R): R =
    underlying.foldLeft(initial)(op)
}
