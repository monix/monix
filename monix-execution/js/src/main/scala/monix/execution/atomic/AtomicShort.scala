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

/** @define createDesc Constructs an [[AtomicShort]] reference, allowing
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
object AtomicShort {
  /** Builds an [[AtomicShort]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Short): AtomicShort =
    new AtomicShort(initialValue)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Short, padding: PaddingStrategy): AtomicShort =
    new AtomicShort(initialValue)

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
  def create(initialValue: Short, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicShort =
    new AtomicShort(initialValue)

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
  def safe(initialValue: Short, padding: PaddingStrategy): AtomicShort =
    new AtomicShort(initialValue)
}
