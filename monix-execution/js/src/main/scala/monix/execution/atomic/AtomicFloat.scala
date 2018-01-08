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

package monix.execution.atomic

/** Atomic references wrapping `Float` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Float` is a primitive.
  */
final class AtomicFloat private[atomic]
  (initialValue: Float) extends AtomicNumber[Float] {

  private[this] var ref = initialValue

  def getAndSet(update: Float): Float = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: Float, update: Float): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: Float): Unit = {
    ref = update
  }

  def get: Float = ref

  def getAndSubtract(v: Float): Float = {
    val c = ref
    ref = ref - v
    c
  }

  def subtractAndGet(v: Float): Float = {
    ref = ref - v
    ref
  }

  def subtract(v: Float): Unit = {
    ref = ref - v
  }

  def getAndAdd(v: Float): Float = {
    val c = ref
    ref = ref + v
    c
  }

  def getAndIncrement(v: Int = 1): Float = {
    val c = ref
    ref = ref + v
    c
  }

  def addAndGet(v: Float): Float = {
    ref = ref + v
    ref
  }

  def incrementAndGet(v: Int = 1): Float = {
    ref = ref + v
    ref
  }

  def add(v: Float): Unit = {
    ref = ref + v
  }

  def increment(v: Int = 1): Unit = {
    ref = ref + v
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Float = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Float = getAndIncrement(-v)
}

/** @define createDesc Constructs an [[AtomicFloat]] reference, allowing
  *         for fine-tuning of the created instance.
  *
  *         A [[PaddingStrategy]] can be provided in order to counter
  *         the "false sharing" problem.
  *
  *         Note that for ''Scala.js'' we aren't applying any padding,
  *         as it doesn't make much sense, since Javascript execution
  *         is single threaded, but this builder is provided for
  *         syntax compatibility anyway across the JVM and Javascript
  *         and we never know how Javascript engines will evolve.
  */
object AtomicFloat {
  /** Builds an [[AtomicFloat]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Float): AtomicFloat =
    new AtomicFloat(initialValue)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Float, padding: PaddingStrategy): AtomicFloat =
    new AtomicFloat(initialValue)

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
  def create(initialValue: Float, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicFloat =
    new AtomicFloat(initialValue)

  /** $createDesc
    *
    * This builder guarantees to construct a safe atomic reference that
    * does not make use of `sun.misc.Unsafe`. On top of platforms that
    * don't support it, notably some versions of Android or on top of
    * the upcoming Java 9, this might be desirable.
    *
    * NOTE that explicit usage of this builder is not usually necessary
    * because [[create]] can auto-detect whether the underlying platform
    * supports `sun.misc.Unsafe` and if it does, then its usage is
    * recommended, because the "safe" atomic instances have overhead.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def safe(initialValue: Float, padding: PaddingStrategy): AtomicFloat =
    new AtomicFloat(initialValue)
}
