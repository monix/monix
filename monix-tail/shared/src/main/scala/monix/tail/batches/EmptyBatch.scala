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

/** Reusable [[Batch]] implementation
  * that's always empty.
  */
object EmptyBatch extends Batch[Nothing] {
  override def cursor() = EmptyCursor
  override def take(n: Int): Batch[Nothing] = EmptyBatch
  override def drop(n: Int): Batch[Nothing] = EmptyBatch
  override def slice(from: Int, until: Int): Batch[Nothing] = EmptyBatch
  override def map[B](f: (Nothing) => B): Batch[B] = EmptyBatch
  override def filter(p: (Nothing) => Boolean): Batch[Nothing] = EmptyBatch
  override def collect[B](pf: PartialFunction[Nothing, B]): Batch[B] = EmptyBatch
  override def foldLeft[R](initial: R)(op: (R, Nothing) => R): R = initial
}
