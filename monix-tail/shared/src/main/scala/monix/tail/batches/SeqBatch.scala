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

/** [[Batch]] implementation that wraps any
  * Scala [[scala.collection.Seq Seq]].
  */
final class SeqBatch[+A](ref: Seq[A], recommendedBatchSize: Int) extends Batch[A] {
  def cursor(): BatchCursor[A] = BatchCursor.fromSeq(ref, recommendedBatchSize)

  override def take(n: Int): Batch[A] =
    new SeqBatch(ref.take(n), recommendedBatchSize)
  override def drop(n: Int): Batch[A] =
    new SeqBatch(ref.drop(n), recommendedBatchSize)
  override def slice(from: Int, until: Int): Batch[A] =
    new SeqBatch(ref.slice(from, until), recommendedBatchSize)
  override def map[B](f: (A) => B): Batch[B] =
    new SeqBatch(ref.map(f), recommendedBatchSize)
  override def filter(p: (A) => Boolean): Batch[A] =
    new SeqBatch(ref.filter(p), recommendedBatchSize)
  override def collect[B](pf: PartialFunction[A, B]): Batch[B] =
    new SeqBatch(ref.collect(pf), recommendedBatchSize)
  override def foldLeft[R](initial: R)(op: (R, A) => R): R =
    ref.foldLeft(initial)(op)
}