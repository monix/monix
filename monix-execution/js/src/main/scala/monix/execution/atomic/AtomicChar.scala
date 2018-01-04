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

/** Atomic references wrapping `Char` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Char` is a primitive.
  */
final class AtomicChar private[atomic]
  (initialValue: Char) extends AtomicNumber[Char] {

  private[this] var ref = initialValue
  private[this] val mask = 255 + 255 * 256

  def getAndSet(update: Char): Char = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: Char, update: Char): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: Char): Unit = {
    ref = update
  }

  def get: Char = ref

  def getAndSubtract(v: Char): Char = {
    val c = ref
    ref = ((ref - v) & mask).asInstanceOf[Char]
    c
  }

  def subtractAndGet(v: Char): Char = {
    ref = ((ref - v) & mask).asInstanceOf[Char]
    ref
  }

  def subtract(v: Char): Unit = {
    ref = ((ref - v) & mask).asInstanceOf[Char]
  }

  def getAndAdd(v: Char): Char = {
    val c = ref
    ref = ((ref + v) & mask).asInstanceOf[Char]
    c
  }

  def getAndIncrement(v: Int = 1): Char = {
    val c = ref
    ref = ((ref + v) & mask).asInstanceOf[Char]
    c
  }

  def addAndGet(v: Char): Char = {
    ref = ((ref + v) & mask).asInstanceOf[Char]
    ref
  }

  def incrementAndGet(v: Int = 1): Char = {
    ref = ((ref + v) & mask).asInstanceOf[Char]
    ref
  }

  def add(v: Char): Unit = {
    ref = ((ref + v) & mask).asInstanceOf[Char]
  }

  def increment(v: Int = 1): Unit = {
    ref = ((ref + v) & mask).asInstanceOf[Char]
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Char = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Char = getAndIncrement(-v)
}

/** @define createDesc Constructs an [[AtomicChar]] reference, allowing
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
object AtomicChar {
  /** Builds an [[AtomicChar]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Char): AtomicChar =
    new AtomicChar(initialValue)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Char, padding: PaddingStrategy): AtomicChar =
    new AtomicChar(initialValue)

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
  def create(initialValue: Char, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicChar =
    new AtomicChar(initialValue)

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
  def safe(initialValue: Char, padding: PaddingStrategy): AtomicChar =
    new AtomicChar(initialValue)
}