/*
 * Copyright (c) 2016 by its authors. Some rights reserved.
 * See the project homepage at: https://sincron.org
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

package monix.execution.atomic

/** Atomic references wrapping `Long` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Long` is a primitive.
  */
final class AtomicLong private[atomic]
  (initialValue: Long) extends AtomicNumber[Long] {

  private[this] var ref = initialValue

  def getAndSet(update: Long): Long = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: Long, update: Long): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: Long): Unit = {
    ref = update
  }

  def get: Long = ref

  def getAndSubtract(v: Long): Long = {
    val c = ref
    ref = ref - v
    c
  }

  def subtractAndGet(v: Long): Long = {
    ref = ref - v
    ref
  }

  def subtract(v: Long): Unit = {
    ref = ref - v
  }

  def getAndAdd(v: Long): Long = {
    val c = ref
    ref = ref + v
    c
  }

  def getAndIncrement(v: Int = 1): Long = {
    val c = ref
    ref = ref + v
    c
  }

  def addAndGet(v: Long): Long = {
    ref = ref + v
    ref
  }

  def incrementAndGet(v: Int = 1): Long = {
    ref = ref + v
    ref
  }

  def add(v: Long): Unit = {
    ref = ref + v
  }

  def increment(v: Int = 1): Unit = {
    ref = ref + v
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Long = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Long = getAndIncrement(-v)
}

object AtomicLong {
  /** Constructs an [[AtomicLong]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Long): AtomicLong =
    new AtomicLong(initialValue)

  /** Constructs an [[AtomicLong]] reference, applying the provided
    * [[PaddingStrategy]] in order to counter the "false sharing"
    * problem.
    *
    * Note that for ''Scala.js'' we aren't applying any padding, as it
    * doesn't make much sense, since Javascript execution is single
    * threaded, but this builder is provided for syntax compatibility
    * anyway across the JVM and Javascript and we never know how
    * Javascript engines will evolve.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Long, padding: PaddingStrategy): AtomicLong =
    new AtomicLong(initialValue)
}
