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

/** Atomic references wrapping `Short` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Short` is a primitive.
  */
final class AtomicShort private[atomic]
  (initialValue: Short) extends AtomicNumber[Short] {

  private[this] var ref = initialValue
  private[this] val mask = 255 + 255 * 256

  def getAndSet(update: Short): Short = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: Short, update: Short): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: Short): Unit = {
    ref = update
  }

  def get: Short = ref

  def getAndSubtract(v: Short): Short = {
    val c = ref
    ref = ((ref - v) & mask).asInstanceOf[Short]
    c
  }

  def subtractAndGet(v: Short): Short = {
    ref = ((ref - v) & mask).asInstanceOf[Short]
    ref
  }

  def subtract(v: Short): Unit = {
    ref = ((ref - v) & mask).asInstanceOf[Short]
  }

  def getAndAdd(v: Short): Short = {
    val c = ref
    ref = ((ref + v) & mask).asInstanceOf[Short]
    c
  }

  def getAndIncrement(v: Int = 1): Short = {
    val c = ref
    ref = ((ref + v) & mask).asInstanceOf[Short]
    c
  }

  def addAndGet(v: Short): Short = {
    ref = ((ref + v) & mask).asInstanceOf[Short]
    ref
  }

  def incrementAndGet(v: Int = 1): Short = {
    ref = ((ref + v) & mask).asInstanceOf[Short]
    ref
  }

  def add(v: Short): Unit = {
    ref = ((ref + v) & mask).asInstanceOf[Short]
  }

  def increment(v: Int = 1): Unit = {
    ref = ((ref + v) & mask).asInstanceOf[Short]
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Short = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Short = getAndIncrement(-v)
}

object AtomicShort {
  /** Constructs an [[AtomicShort]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Short): AtomicShort =
    new AtomicShort(initialValue)

  /** Constructs an [[AtomicShort]] reference, applying the provided
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
  def withPadding(initialValue: Short, padding: PaddingStrategy): AtomicShort =
    new AtomicShort(initialValue)
}