/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.execution.atomic.internal.{ BoxedLong, Factory }

/** Atomic references wrapping `Long` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Long` is a primitive.
  */
final class AtomicLong private (private[this] val ref: BoxedLong) extends AtomicNumber[Long] {

  def get(): Long = ref.volatileGet()
  def set(update: Long): Unit = ref.volatileSet(update)

  def compareAndSet(expect: Long, update: Long): Boolean =
    ref.compareAndSet(expect, update)

  def getAndSet(update: Long): Long =
    ref.getAndSet(update)

  def lazySet(update: Long): Unit =
    ref.lazySet(update)

  def increment(v: Int = 1): Unit = {
    ref.getAndAdd(v.asInstanceOf[Long])
    ()
  }

  def incrementAndGet(v: Int = 1): Long =
    ref.getAndAdd(v.asInstanceOf[Long]) + v

  def getAndIncrement(v: Int = 1): Long =
    ref.getAndAdd(v.asInstanceOf[Long])

  def getAndAdd(v: Long): Long =
    ref.getAndAdd(v)

  def addAndGet(v: Long): Long =
    ref.getAndAdd(v) + v

  def add(v: Long): Unit = {
    ref.getAndAdd(v)
    ()
  }

  def subtract(v: Long): Unit =
    add(-v)

  def getAndSubtract(v: Long): Long =
    getAndAdd(-v)

  def subtractAndGet(v: Long): Long =
    addAndGet(-v)

  def decrement(v: Int = 1): Unit =
    increment(-v)

  def decrementAndGet(v: Int = 1): Long =
    incrementAndGet(-v)

  def getAndDecrement(v: Int = 1): Long =
    getAndIncrement(-v)

  override def toString: String =
    s"AtomicLong(${ref.volatileGet()})"
}

/** @define createDesc Constructs an [[AtomicLong]] reference, allowing
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
object AtomicLong {
  /** Builds an [[AtomicLong]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Long): AtomicLong =
    withPadding(initialValue, NoPadding)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Long, padding: PaddingStrategy): AtomicLong =
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
  def create(initialValue: Long, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicLong = {
    new AtomicLong(
      Factory.newBoxedLong(
        initialValue,
        boxStrategyToPaddingStrategy(padding),
        true, // allowUnsafe
        allowPlatformIntrinsics
      )
    )
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
  def safe(initialValue: Long, padding: PaddingStrategy): AtomicLong = {
    new AtomicLong(
      Factory.newBoxedLong(
        initialValue,
        boxStrategyToPaddingStrategy(padding),
        false, // allowUnsafe
        false // allowPlatformIntrinsics
      )
    )
  }
}
