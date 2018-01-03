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

import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.atomic.{BoxedInt, Factory}

/** Atomic references wrapping `Int` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Int` is a primitive.
  */
final class AtomicInt private (private[this] val ref: BoxedInt)
  extends AtomicNumber[Int] {

  def get: Int = ref.volatileGet()
  def set(update: Int): Unit = ref.volatileSet(update)

  def compareAndSet(expect: Int, update: Int): Boolean =
    ref.compareAndSet(expect, update)

  def getAndSet(update: Int): Int =
    ref.getAndSet(update)

  def lazySet(update: Int): Unit =
    ref.lazySet(update)

  def increment(v: Int = 1): Unit =
    ref.getAndAdd(v)

  def incrementAndGet(v: Int = 1): Int =
    ref.getAndAdd(v) + v

  def getAndIncrement(v: Int = 1): Int =
    ref.getAndAdd(v)

  def getAndAdd(v: Int): Int =
    ref.getAndAdd(v)

  def addAndGet(v: Int): Int =
    ref.getAndAdd(v) + v

  def add(v: Int): Unit =
    ref.getAndAdd(v)

  def subtract(v: Int): Unit =
    add(-v)

  def getAndSubtract(v: Int): Int =
    getAndAdd(-v)

  def subtractAndGet(v: Int): Int =
    addAndGet(-v)

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Int = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Int = getAndIncrement(-v)

  override def toString: String = s"AtomicInt(${ref.volatileGet()})"
}

/** @define createDesc Constructs an [[AtomicInt]] reference, allowing
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
object AtomicInt {
  /** Builds an [[AtomicInt]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Int): AtomicInt =
    withPadding(initialValue, NoPadding)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Int, padding: PaddingStrategy): AtomicInt =
    create(initialValue, padding, allowPlatformIntrinsics = true)

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
  def create(initialValue: Int, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicInt = {
    new AtomicInt(Factory.newBoxedInt(
      initialValue,
      boxStrategyToPaddingStrategy(padding),
      true, // allowUnsafe
      allowPlatformIntrinsics
    ))
  }

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
  def safe(initialValue: Int, padding: PaddingStrategy): AtomicInt = {
    new AtomicInt(Factory.newBoxedInt(
      initialValue,
      boxStrategyToPaddingStrategy(padding),
      false, // allowUnsafe
      false  // allowJava8Intrinsics
    ))
  }
}
