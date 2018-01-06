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


/** [[Batch]] implementation specialized for `Double`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayBatch ArrayBatch]]
  * implementation, which is `@specialized`. Using `DoublesBatch`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class DoublesBatch(underlying: ArrayBatch[Double])
  extends Batch[Double] {

  override def cursor(): DoublesCursor =
    new DoublesCursor(underlying.cursor())

  override def take(n: Int): DoublesBatch =
    new DoublesBatch(underlying.take(n))
  override def drop(n: Int): DoublesBatch =
    new DoublesBatch(underlying.drop(n))
  override def slice(from: Int, until: Int): DoublesBatch =
    new DoublesBatch(underlying.slice(from, until))
  override def filter(p: (Double) => Boolean): DoublesBatch =
    new DoublesBatch(underlying.filter(p))

  override def map[B](f: (Double) => B): ArrayBatch[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Double, B]): ArrayBatch[B] =
    underlying.collect(pf)

  override def foldLeft[R](initial: R)(op: (R, Double) => R): R =
    underlying.foldLeft(initial)(op)
}
