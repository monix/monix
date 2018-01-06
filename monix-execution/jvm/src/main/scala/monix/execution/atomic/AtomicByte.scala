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

/** Atomic references wrapping `Byte` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Byte` is a primitive.
  */
final class AtomicByte private (private[this] val ref: BoxedInt)
  extends AtomicNumber[Byte] {

  private[this] val mask = 255

  def get: Byte = (ref.volatileGet() & mask).asInstanceOf[Byte]
  def set(update: Byte): Unit = ref.volatileSet(update)

  def lazySet(update: Byte): Unit =
    ref.lazySet(update)

  def compareAndSet(expect: Byte, update: Byte): Boolean =
    ref.compareAndSet(expect, update)

  def getAndSet(update: Byte): Byte =
    (ref.getAndSet(update) & mask).asInstanceOf[Byte]

  def increment(v: Int = 1): Unit =
    ref.getAndAdd(v)

  def add(v: Byte): Unit =
    ref.getAndAdd(v)

  def incrementAndGet(v: Int = 1): Byte =
    ((ref.getAndAdd(v) + v) & mask).asInstanceOf[Byte]

  def addAndGet(v: Byte): Byte =
    ((ref.getAndAdd(v) + v) & mask).asInstanceOf[Byte]

  def getAndIncrement(v: Int = 1): Byte =
    (ref.getAndAdd(v) & mask).asInstanceOf[Byte]

  def getAndAdd(v: Byte): Byte =
    (ref.getAndAdd(v) & mask).asInstanceOf[Byte]

  def subtract(v: Byte): Unit =
    ref.getAndAdd(-v.asInstanceOf[Int])
  def subtractAndGet(v: Byte): Byte =
    ((ref.getAndAdd(-v.asInstanceOf[Int]) - v) & mask).asInstanceOf[Byte]
  def getAndSubtract(v: Byte): Byte =
    (ref.getAndAdd(-v.asInstanceOf[Int]) & mask).asInstanceOf[Byte]

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Byte = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Byte = getAndIncrement(-v)
}

/** @define createDesc Constructs an [[AtomicByte]] reference, allowing
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
object AtomicByte {
  /** Builds an [[AtomicByte]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Byte): AtomicByte =
    withPadding(initialValue, NoPadding)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Byte, padding: PaddingStrategy): AtomicByte =
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
  def create(initialValue: Byte, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicByte =
    new AtomicByte(Factory.newBoxedInt(
      initialValue,
      boxStrategyToPaddingStrategy(padding),
      true, // allowUnsafe
      allowPlatformIntrinsics
    ))

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
  def safe(initialValue: Byte, padding: PaddingStrategy): AtomicByte = {
    new AtomicByte(Factory.newBoxedInt(
      initialValue,
      boxStrategyToPaddingStrategy(padding),
      false, // allowUnsafe
      false  // allowJava8Intrinsics
    ))
  }
}
