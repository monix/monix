/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

/** [[Batch]] implementation specialized for `Char`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayBatch ArrayBatch]]
  * implementation, which is `@specialized`. Using `CharsBatch`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class CharsBatch(underlying: ArrayBatch[Char]) extends Batch[Char] {

  override def cursor(): CharsCursor =
    new CharsCursor(underlying.cursor())

  override def take(n: Int): CharsBatch =
    new CharsBatch(underlying.take(n))
  override def drop(n: Int): CharsBatch =
    new CharsBatch(underlying.drop(n))
  override def slice(from: Int, until: Int): CharsBatch =
    new CharsBatch(underlying.slice(from, until))
  override def filter(p: (Char) => Boolean): CharsBatch =
    new CharsBatch(underlying.filter(p))

  override def map[B](f: (Char) => B): ArrayBatch[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Char, B]): ArrayBatch[B] =
    underlying.collect(pf)

  override def foldLeft[R](initial: R)(op: (R, Char) => R): R =
    underlying.foldLeft(initial)(op)
}
