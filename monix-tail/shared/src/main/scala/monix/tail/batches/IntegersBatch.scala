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

/** [[Batch]] implementation specialized for `Int`.
  *
  * Under the hood it uses an [[monix.tail.batches.ArrayBatch ArrayBatch]]
  * implementation, which is `@specialized`. Using `IntegersBatch`
  * might be desirable instead for `isInstanceOf` checks.
  */
final class IntegersBatch(underlying: ArrayBatch[Int])
  extends Batch[Int] {

  override def cursor(): IntegersCursor =
    new IntegersCursor(underlying.cursor())

  override def take(n: Int): IntegersBatch =
    new IntegersBatch(underlying.take(n))
  override def drop(n: Int): IntegersBatch =
    new IntegersBatch(underlying.drop(n))
  override def slice(from: Int, until: Int): IntegersBatch =
    new IntegersBatch(underlying.slice(from, until))
  override def filter(p: (Int) => Boolean): IntegersBatch =
    new IntegersBatch(underlying.filter(p))

  override def map[B](f: (Int) => B): ArrayBatch[B] =
    underlying.map(f)
  override def collect[B](pf: PartialFunction[Int, B]): ArrayBatch[B] =
    underlying.collect(pf)

  override def foldLeft[R](initial: R)(op: (R, Int) => R): R =
    underlying.foldLeft(initial)(op)
}
