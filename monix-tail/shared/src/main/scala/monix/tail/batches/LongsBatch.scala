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

package monix.tail.batches

/** [[Batch]] implementation specialized for `Long`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayBatch ArrayBatch]]
  * implementation, which is `@specialized`. Using `LongsBatch`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class LongsBatch(underlying: ArrayBatch[Long])
  extends Batch[Long] {

  override def cursor(): LongsCursor =
    new LongsCursor(underlying.cursor())

  override def take(n: Int): LongsBatch =
    new LongsBatch(underlying.take(n))
  override def drop(n: Int): LongsBatch =
    new LongsBatch(underlying.drop(n))
  override def slice(from: Int, until: Int): LongsBatch =
    new LongsBatch(underlying.slice(from, until))
  override def filter(p: (Long) => Boolean): LongsBatch =
    new LongsBatch(underlying.filter(p))

  override def map[B](f: (Long) => B): ArrayBatch[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Long, B]): ArrayBatch[B] =
    underlying.collect(pf)

  override def foldLeft[R](initial: R)(op: (R, Long) => R): R =
    underlying.foldLeft(initial)(op)
}
