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

/** Atomic references wrapping `Double` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Double` is a primitive.
  */
final class AtomicDouble private[atomic]
  (initialValue: Double) extends AtomicNumber[Double] {

  private[this] var ref = initialValue

  def getAndSet(update: Double): Double = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: Double, update: Double): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: Double): Unit = {
    ref = update
  }

  def get: Double = ref

  def getAndSubtract(v: Double): Double = {
    val c = ref
    ref = ref - v
    c
  }

  def subtractAndGet(v: Double): Double = {
    ref = ref - v
    ref
  }

  def subtract(v: Double): Unit = {
    ref = ref - v
  }

  def getAndAdd(v: Double): Double = {
    val c = ref
    ref = ref + v
    c
  }

  def getAndIncrement(v: Int = 1): Double = {
    val c = ref
    ref = ref + v
    c
  }

  def addAndGet(v: Double): Double = {
    ref = ref + v
    ref
  }

  def incrementAndGet(v: Int = 1): Double = {
    ref = ref + v
    ref
  }

  def add(v: Double): Unit = {
    ref = ref + v
  }

  def increment(v: Int = 1): Unit = {
    ref = ref + v
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Double = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Double = getAndIncrement(-v)
}

object AtomicDouble {
  /** Builds an [[AtomicDouble]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Double): AtomicDouble =
      new AtomicDouble(initialValue)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Double, padding: PaddingStrategy): AtomicDouble =
    new AtomicDouble(initialValue)

  /** $createDesc
    *
    * Also this builder on top Java 8 also allows for turning off the
    * Java 8 intrinsics, thus forcing usage of CAS-loops for
    * `getAndSet` and for `getAndAdd`.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    * @param allowPlatformIntrinsics is a boolean parameter that specifies whether
    *        the instance is allowed to use the Java 8 optimized operations
    *        for `getAndSet` and for `getAndAdd`
    */
  def create(initialValue: Double, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicDouble =
    new AtomicDouble(initialValue)
}
