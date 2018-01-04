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

/** [[Batch]] implementation specialized for `Byte`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayBatch ArrayBatch]]
  * implementation, which is `@specialized`. Using `BytesBatch`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class BytesBatch(underlying: ArrayBatch[Byte])
  extends Batch[Byte] {

  override def cursor(): BytesCursor =
    new BytesCursor(underlying.cursor())

  override def take(n: Int): BytesBatch =
    new BytesBatch(underlying.take(n))
  override def drop(n: Int): BytesBatch =
    new BytesBatch(underlying.drop(n))
  override def slice(from: Int, until: Int): BytesBatch =
    new BytesBatch(underlying.slice(from, until))
  override def filter(p: (Byte) => Boolean): BytesBatch =
    new BytesBatch(underlying.filter(p))

  override def map[B](f: (Byte) => B): ArrayBatch[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Byte, B]): ArrayBatch[B] =
    underlying.collect(pf)

  override def foldLeft[R](initial: R)(op: (R, Byte) => R): R =
    underlying.foldLeft(initial)(op)
}
