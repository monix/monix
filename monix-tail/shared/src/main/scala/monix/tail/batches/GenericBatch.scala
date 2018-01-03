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

/** Reusable [[Batch]] base class that can
  * be used for implementing generators that simply modify their underlying
  * cursor reference.
  */
abstract class GenericBatch[+A] extends Batch[A] {
  /** Transforms this generator with a mapping function for
    * the underlying [[BatchCursor]].
    *
    * NOTE: application of this function can be
    * either strict or lazy (depending on the underlying generator type),
    * but it does not modify the original collection.
    *
    *  - if this generator is an `Array`-backed generator, the given
    *    function will be applied immediately to create a new array with
    *    an accompanying generator.
    *  - if this generator is backed by `Iterable`, then the given
    *    function will be applied lazily, on demand
    *
    * @param f is the function used
    *
    * @return a new generator with its underlying sequence transformed
    *         by the mapping function
    */
  protected def transform[B](f: BatchCursor[A] => BatchCursor[B]): Batch[B] = {
    val self = this
    new GenericBatch[B] { def cursor(): BatchCursor[B] = f(self.cursor()) }
  }

  override final def take(n: Int): Batch[A] =
    transform(_.take(n))
  override final def drop(n: Int): Batch[A] =
    transform(_.drop(n))
  override final def slice(from: Int, until: Int): Batch[A] =
    transform(_.slice(from, until))
  override final def map[B](f: (A) => B): Batch[B] =
    transform(_.map(f))
  override final def filter(p: (A) => Boolean): Batch[A] =
    transform(_.filter(p))
  override final def collect[B](pf: PartialFunction[A, B]): Batch[B] =
    transform(_.collect(pf))
  override final def foldLeft[R](initial: R)(op: (R, A) => R): R =
    cursor().foldLeft(initial)(op)
}