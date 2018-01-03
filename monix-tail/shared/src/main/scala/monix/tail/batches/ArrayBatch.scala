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

import scala.collection.mutable.ArrayBuilder
import scala.reflect.ClassTag

/** [[Batch]] implementation that wraps
  * an array, based on [[ArrayCursor]].
  */
final class ArrayBatch[@specialized(Boolean, Byte, Char, Int, Long, Double) A]
  (ref: Array[A], offset: Int, length: Int, newBuilder: () => ArrayBuilder[A])
  extends Batch[A] {

  def this(ref: Array[A], offset: Int, length: Int)(implicit tag: ClassTag[A]) =
    this(ref, offset, length, () => ArrayBuilder.make[A]())

  override def cursor(): ArrayCursor[A] =
    new ArrayCursor[A](ref, offset, length, newBuilder)

  override def take(n: Int): ArrayBatch[A] = {
    val ref = cursor().take(n)
    new ArrayBatch(ref.array, ref.offset, ref.length, newBuilder)
  }

  override def drop(n: Int): ArrayBatch[A] = {
    val ref = cursor().drop(n)
    new ArrayBatch(ref.array, ref.offset, ref.length, newBuilder)
  }

  override def slice(from: Int, until: Int): ArrayBatch[A] = {
    val ref = cursor().slice(from, until)
    new ArrayBatch(ref.array, ref.offset, ref.length, newBuilder)
  }

  override def map[B](f: (A) => B): ArrayBatch[B] = {
    val ref = cursor().map(f)
    Batch.fromAnyArray[B](ref.array, 0, ref.length)
  }

  override def filter(p: (A) => Boolean): ArrayBatch[A] = {
    val ref = cursor().filter(p)
    new ArrayBatch(ref.array, ref.offset, ref.length, newBuilder)
  }

  override def collect[B](pf: PartialFunction[A, B]): ArrayBatch[B] = {
    val ref = cursor().collect(pf)
    Batch.fromAnyArray[B](ref.array, 0, ref.length)
  }

  override def foldLeft[R](initial: R)(op: (R, A) => R): R =
    cursor().foldLeft(initial)(op)
}