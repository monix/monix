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

import minitest.SimpleTestSuite
import monix.execution.atomic.PaddingStrategy._

abstract class AtomicNumberSuite[A, R <: AtomicNumber[A]]
  (builder: AtomicBuilder[A, R], strategy: PaddingStrategy,
  value: A, maxValue: A, minValue: A, hasOverflow: Boolean = true,
  allowPlatformIntrinsics: Boolean, allowUnsafe: Boolean)(implicit ev: Numeric[A])
  extends SimpleTestSuite {

  def Atomic(initial: A): R = {
    if (allowUnsafe)
      builder.buildInstance(initial, strategy, allowPlatformIntrinsics)
    else
      builder.buildSafeInstance(initial, strategy)
  }

  val zero = ev.zero
  val one = ev.one
  val two = ev.plus(ev.one, ev.one)

  test("should get()") {
    assert(Atomic(value).get == value)
    assert(Atomic(maxValue).get == maxValue)
    assert(Atomic(minValue).get == minValue)
  }

  test("should set()") {
    val r = Atomic(zero)
    r.set(value)
    assert(r.get == value)
    r.set(minValue)
    assert(r.get == minValue)
    r.set(maxValue)
    assert(r.get == maxValue)
  }

  test("should compareAndSet()") {
    val r = Atomic(zero)
    assert(r.compareAndSet(zero, one))
    assert(!r.compareAndSet(zero, one))
    assert(r.get == one)
  }

  test("should getAndSet()") {
    val r = Atomic(zero)
    assert(r.getAndSet(one) == zero)
    assert(r.getAndSet(value) == one)
    assert(r.getAndSet(minValue) == value)
    assert(r.getAndSet(maxValue) == minValue)
    assert(r.get == maxValue)
  }

  test("should increment()") {
    val r = Atomic(value)
    r.increment()
    assert(r.get == ev.plus(value, one))
    r.increment()
    assert(r.get == ev.plus(value, ev.plus(one, one)))
  }

  test("should increment() and overflow on max") {
    if (!hasOverflow) ignore()
    val r = Atomic(maxValue)
    r.increment()
    assert(r.get == minValue)
  }

  test("should increment(value)") {
    val r = Atomic(value)
    r.increment(ev.toInt(value))
    assert(r.get == ev.plus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should decrement()") {
    val r = Atomic(value)
    r.decrement()
    assert(r.get == ev.minus(value, one))
    r.decrement()
    assert(r.get == ev.minus(value, ev.plus(one, one)))
  }

  test("should decrement(value)") {
    val r = Atomic(value)
    r.decrement(ev.toInt(value))
    assert(r.get == ev.minus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should decrement() and overflow on min") {
    if (!hasOverflow) ignore()
    val r = Atomic(minValue)
    r.decrement()
    assert(r.get == maxValue)
  }

  test("should incrementAndGet()") {
    val r = Atomic(value)
    assert(r.incrementAndGet() == ev.plus(value, one))
    assert(r.incrementAndGet() == ev.plus(value, ev.plus(one, one)))
  }

  test("should incrementAndGet(value)") {
    val r = Atomic(value)
    assert(r.incrementAndGet(ev.toInt(value)) == ev.plus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should incrementAndGet() and overflow on max") {
    if (!hasOverflow) ignore()
    val r = Atomic(maxValue)
    assertEquals(r.incrementAndGet(), minValue)
  }

  test("should decrementAndGet()") {
    val r = Atomic(value)
    assert(r.decrementAndGet() == ev.minus(value, one))
    assert(r.decrementAndGet() == ev.minus(value, ev.plus(one, one)))
  }

  test("should decrementAndGet(value)") {
    val r = Atomic(value)
    assert(r.decrementAndGet(ev.toInt(value)) == ev.minus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should getAndIncrement()") {
    val r = Atomic(value)
    assert(r.getAndIncrement() == value)
    assert(r.getAndIncrement() == ev.plus(value, one))
    assert(r.get == ev.plus(value, two))
  }

  test("should getAndIncrement(value)") {
    val r = Atomic(value)
    assert(r.getAndIncrement(2) == value)
    assert(r.getAndIncrement(2) == ev.plus(value, two))
    assert(r.get == ev.plus(value, ev.plus(two, two)))
  }

  test("should getAndIncrement and overflow on max") {
    if (!hasOverflow) ignore()
    val r = Atomic(maxValue)
    assertEquals(r.getAndIncrement(), maxValue)
    assertEquals(r.get, minValue)
  }

  test("should getAndDecrement()") {
    val r = Atomic(value)
    assert(r.getAndDecrement() == value)
    assert(r.getAndDecrement() == ev.minus(value, one))
    assert(r.get == ev.minus(value, two))
  }

  test("should getAndDecrement(value)") {
    val r = Atomic(value)
    assert(r.getAndDecrement(2) == value)
    assert(r.getAndDecrement(2) == ev.minus(value, two))
    assert(r.get == ev.minus(value, ev.plus(two, two)))
  }

  test("should addAndGet(value)") {
    val r = Atomic(value)
    assert(r.addAndGet(value) == ev.plus(value, value))
    assert(r.addAndGet(value) == ev.plus(value, ev.plus(value, value)))
  }

  test("should addAndGet and overflow on max") {
    if (!hasOverflow) ignore()
    val r = Atomic(maxValue)
    assertEquals(r.addAndGet(ev.one), minValue)
    assertEquals(r.get, minValue)
  }

  test("should add(value)") {
    val r = Atomic(value)
    r.add(value)
    assert(r.get == ev.plus(value, value))
    r.add(value)
    assert(r.get == ev.plus(value, ev.plus(value, value)))
  }

  test("should add(value) and overflow on max") {
    if (!hasOverflow) ignore()
    val r = Atomic(maxValue)
    r.add(ev.one)
    assertEquals(r.get, minValue)
  }

  test("should getAndAdd(value)") {
    val r = Atomic(value)
    assert(r.getAndAdd(value) == value)
    assert(r.get == ev.plus(value, value))
  }

  test("should getAndAdd and overflow on max") {
    if (!hasOverflow) ignore()
    val r = Atomic(maxValue)
    assertEquals(r.getAndAdd(ev.one), maxValue)
    assertEquals(r.get, minValue)
  }

  test("should subtractAndGet(value)") {
    val r = Atomic(value)
    assert(r.subtractAndGet(value) == ev.minus(value, value))
    assert(r.subtractAndGet(value) == ev.minus(value, ev.plus(value, value)))
  }

  test("should subtractAndGet(value) and overflow on min") {
    if (!hasOverflow) ignore()
    val r = Atomic(minValue)
    assertEquals(r.subtractAndGet(ev.one), maxValue)
  }

  test("should subtract(value)") {
    val r = Atomic(value)
    r.subtract(value)
    assert(r.get == ev.minus(value, value))
    r.subtract(value)
    assert(r.get == ev.minus(value, ev.plus(value, value)))
  }

  test("should subtract(value) and overflow on min") {
    if (!hasOverflow) ignore()
    val r = Atomic(minValue)
    r.subtract(ev.one)
    assertEquals(r.get, maxValue)
  }

  test("should getAndSubtract(value)") {
    val r = Atomic(value)
    assert(r.getAndSubtract(value) == value)
    assert(r.get == zero)
  }

  test("should getAndSubtract(value) and overflow on min") {
    if (!hasOverflow) ignore()
    val r = Atomic(minValue)
    assertEquals(r.getAndSubtract(ev.one), minValue)
    assertEquals(r.get, maxValue)
  }

  test("should transform(inline #1)") {
    val r = Atomic(value)
    r.transform(x => ev.plus(x, x))
    assert(r.get == ev.plus(value, value))
  }

  test("should transform(inline #2)") {
    val r = Atomic(value)
    r.transform(ev.plus(one, _))
    assert(r.get == ev.plus(one, value))
  }

  test("should transform(function)") {
    val r = Atomic(value)
    def fn(x: A): A = ev.plus(x, x)
    r.transform(fn)
    assert(r.get == ev.plus(value, value))
  }

  test("should transformAndGet(inline #1)") {
    val r = Atomic(value)
    assert(r.transformAndGet(x => ev.plus(x, x)) == ev.plus(value, value))
  }

  test("should transformAndGet(inline #2)") {
    val r = Atomic(value)
    assert(r.transformAndGet(ev.plus(one, _)) == ev.plus(one, value))
  }

  test("should transformAndGet(function)") {
    val r = Atomic(value)
    def fn(x: A) = ev.plus(x,x)
    assert(r.transformAndGet(fn) == ev.plus(value, value))
  }

  test("should getAndTransform(inline #1)") {
    val r = Atomic(value)
    assert(r.getAndTransform(x => ev.plus(x, x)) == value)
    assert(r.get == ev.plus(value, value))
  }

  test("should getAndTransform(inline #2)") {
    val r = Atomic(value)
    assert(r.getAndTransform(ev.plus(one, _)) == value)
    assert(r.get == ev.plus(one, value))
  }

  test("should getAndTransform(function)") {
    val r = Atomic(value)
    def fn(x: A) = ev.plus(x,x)
    assert(r.getAndTransform(fn) == value)
    assert(r.get == ev.plus(value, value))
  }

  test("should transformAndExtract()") {
    val r = Atomic(value)
    assert(r.transformAndExtract(x => (ev.plus(value, one), ev.plus(x, x))) == ev.plus(value, one))
    assert(r.get == ev.plus(value, value))
  }

  test("should transformAndExtract()") {
    val r = Atomic(value)
    assert(r.transformAndExtract(x => (ev.plus(value, one), ev.plus(x, x))) == ev.plus(value, one))
    assert(r.get == ev.plus(value, value))
  }

  test("should maybe overflow on max") {
    val r = Atomic(maxValue)
    r.increment()
    assert(r.get == ev.plus(maxValue, one))
  }

  test("should maybe overflow on min") {
    val r = Atomic(minValue)
    r.decrement()
    assert(r.get == ev.minus(minValue, one))
  }
}

abstract class AtomicDoubleSuite(strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean, allowUnsafe: Boolean)
  extends AtomicNumberSuite[Double, AtomicDouble](
  Atomic.builderFor(0.0), strategy, 17.23, Double.MaxValue, Double.MinValue, hasOverflow = false,
  allowPlatformIntrinsics, allowUnsafe) {

  test("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
    assert(Atomic(Double.MinPositiveValue).get == Double.MinPositiveValue)
    assert(Atomic(Double.NaN).get.isNaN)
    assert(Atomic(Double.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Double.PositiveInfinity).get.isPosInfinity)
  }
}

abstract class AtomicFloatSuite(strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean, allowUnsafe: Boolean)
  extends AtomicNumberSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), strategy, 17.23f, Float.MaxValue, Float.MinValue, hasOverflow = false,
  allowPlatformIntrinsics, allowUnsafe) {

  test("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
    assert(Atomic(Float.MinPositiveValue).get == Float.MinPositiveValue)
    assert(Atomic(Float.NaN).get.isNaN)
    assert(Atomic(Float.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Float.PositiveInfinity).get.isPosInfinity)
  }
}

// -- NoPadding (Java 8)

object AtomicDoubleNoPaddingSuite extends AtomicDoubleSuite(
  NoPadding, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatNoPaddingSuite extends AtomicFloatSuite(
  NoPadding, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongNoPaddingSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), NoPadding, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntNoPaddingSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortNoPaddingSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteNoPaddingSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharNoPaddingSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyNoPaddingSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], NoPadding, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// -- Left64 (Java 8)

object AtomicDoubleLeft64Suite extends AtomicDoubleSuite(Left64, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatLeft64Suite extends AtomicFloatSuite(Left64, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongLeft64Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left64, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntLeft64Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortLeft64Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteLeft64Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharLeft64Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyLeft64Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// -- Right64 (Java 8)

object AtomicDoubleRight64Suite extends AtomicDoubleSuite(Right64, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatRight64Suite extends AtomicFloatSuite(Right64, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongRight64Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right64, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntRight64Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortRight64Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteRight64Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharRight64Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyRight64Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// -- LeftRight128 (Java 8)

object AtomicDoubleLeftRight128Suite extends AtomicDoubleSuite(LeftRight128, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatLeftRight128Suite extends AtomicFloatSuite(LeftRight128, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongLeftRight128Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntLeftRight128Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortLeftRight128Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteLeftRight128Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharLeftRight128Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyLeftRight128Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// -- Left128 (Java 8)

object AtomicDoubleLeft128Suite extends AtomicDoubleSuite(Left128, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatLeft128Suite extends AtomicFloatSuite(Left128, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongLeft128Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntLeft128Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortLeft128Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteLeft128Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharLeft128Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyLeft128Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// -- Right128 (Java 8)

object AtomicDoubleRight128Suite extends AtomicDoubleSuite(Right128, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatRight128Suite extends AtomicFloatSuite(Right128, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongRight128Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntRight128Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortRight128Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteRight128Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharRight128Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyRight128Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// -- LeftRight256 (Java 8)

object AtomicDoubleLeftRight256Suite extends AtomicDoubleSuite(LeftRight256, allowPlatformIntrinsics = true, allowUnsafe = true)
object AtomicFloatLeftRight256Suite extends AtomicFloatSuite(LeftRight256, allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicLongLeftRight256Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight256, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicIntLeftRight256Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicShortLeftRight256Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicByteLeftRight256Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicCharLeftRight256Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

object AtomicNumberAnyLeftRight256Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight256, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = true, allowUnsafe = true)

// ------------------ Java 7

// -- NoPadding (Java 7)

object AtomicDoubleNoPaddingJava7Suite extends AtomicDoubleSuite(NoPadding, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatNoPaddingJava7Suite extends AtomicFloatSuite(NoPadding, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongNoPaddingJava7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), NoPadding, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntNoPaddingJava7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortNoPaddingJava7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteNoPaddingJava7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharNoPaddingJava7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyNoPaddingJava7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], NoPadding, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// -- Left64 (Java 7)

object AtomicDoubleLeft64Java7Suite extends AtomicDoubleSuite(Left64, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatLeft64Java7Suite extends AtomicFloatSuite(Left64, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongLeft64Java7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left64, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntLeft64Java7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortLeft64Java7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteLeft64Java7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharLeft64Java7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyLeft64Java7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// -- Right64 (Java 7)

object AtomicDoubleRight64Java7Suite extends AtomicDoubleSuite(Right64, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatRight64Java7Suite extends AtomicFloatSuite(Right64, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongRight64Java7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right64, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntRight64Java7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortRight64Java7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteRight64Java7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharRight64Java7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyRight64Java7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// -- LeftRight128 (Java 7)

object AtomicDoubleLeftRight128Java7Suite extends AtomicDoubleSuite(LeftRight128, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatLeftRight128Java7Suite extends AtomicFloatSuite(LeftRight128, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongLeftRight128Java7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntLeftRight128Java7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortLeftRight128Java7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteLeftRight128Java7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharLeftRight128Java7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyLeftRight128Java7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// -- Left128 (Java 7)

object AtomicDoubleLeft128Java7Suite extends AtomicDoubleSuite(Left128, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatLeft128Java7Suite extends AtomicFloatSuite(Left128, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongLeft128Java7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntLeft128Java7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortLeft128Java7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteLeft128Java7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharLeft128Java7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyLeft128Java7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// -- Right128 (Java 7)

object AtomicDoubleRight128Java7Suite extends AtomicDoubleSuite(Right128, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatRight128Java7Suite extends AtomicFloatSuite(Right128, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongRight128Java7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntRight128Java7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortRight128Java7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteRight128Java7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharRight128Java7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyRight128Java7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// -- LeftRight256 (Java 7)

object AtomicDoubleLeftRight256Java7Suite extends AtomicDoubleSuite(LeftRight256, allowPlatformIntrinsics = false, allowUnsafe = true)
object AtomicFloatLeftRight256Java7Suite extends AtomicFloatSuite(LeftRight256, allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicLongLeftRight256Java7Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight256, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicIntLeftRight256Java7Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicShortLeftRight256Java7Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicByteLeftRight256Java7Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicCharLeftRight256Java7Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

object AtomicNumberAnyLeftRight256Java7Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight256, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = true)

// ------------------ Java X

// -- NoPadding (Java X)

object AtomicDoubleNoPaddingJavaXSuite extends AtomicDoubleSuite(
  NoPadding, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatNoPaddingJavaXSuite extends AtomicFloatSuite(
  NoPadding, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongNoPaddingJavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), NoPadding, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntNoPaddingJavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortNoPaddingJavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteNoPaddingJavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharNoPaddingJavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyNoPaddingJavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], NoPadding, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

// -- Left64 (Java X)

object AtomicDoubleLeft64JavaXSuite extends AtomicDoubleSuite(Left64, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatLeft64JavaXSuite extends AtomicFloatSuite(Left64, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongLeft64JavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left64, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntLeft64JavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortLeft64JavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteLeft64JavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharLeft64JavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyLeft64JavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

// -- Right64 (Java X)

object AtomicDoubleRight64JavaXSuite extends AtomicDoubleSuite(Right64, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatRight64JavaXSuite extends AtomicFloatSuite(Right64, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongRight64JavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right64, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntRight64JavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortRight64JavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteRight64JavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharRight64JavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyRight64JavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

// -- LeftRight128 (Java X)

object AtomicDoubleLeftRight128JavaXSuite extends AtomicDoubleSuite(LeftRight128, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatLeftRight128JavaXSuite extends AtomicFloatSuite(LeftRight128, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongLeftRight128JavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntLeftRight128JavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortLeftRight128JavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteLeftRight128JavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharLeftRight128JavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyLeftRight128JavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

// -- Left128 (Java X)

object AtomicDoubleLeft128JavaXSuite extends AtomicDoubleSuite(Left128, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatLeft128JavaXSuite extends AtomicFloatSuite(Left128, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongLeft128JavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntLeft128JavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortLeft128JavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteLeft128JavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharLeft128JavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyLeft128JavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

// -- Right128 (Java X)

object AtomicDoubleRight128JavaXSuite extends AtomicDoubleSuite(Right128, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatRight128JavaXSuite extends AtomicFloatSuite(Right128, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongRight128JavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right128, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntRight128JavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortRight128JavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteRight128JavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharRight128JavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyRight128JavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

// -- LeftRight256 (Java X)

object AtomicDoubleLeftRight256JavaXSuite extends AtomicDoubleSuite(LeftRight256, allowPlatformIntrinsics = false, allowUnsafe = false)
object AtomicFloatLeftRight256JavaXSuite extends AtomicFloatSuite(LeftRight256, allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicLongLeftRight256JavaXSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight256, -782L, Long.MaxValue, Long.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicIntLeftRight256JavaXSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, 782, Int.MaxValue, Int.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicShortLeftRight256JavaXSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, 782.toShort, Short.MaxValue, Short.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicByteLeftRight256JavaXSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, 782.toByte, Byte.MaxValue, Byte.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicCharLeftRight256JavaXSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, 782.toChar, Char.MaxValue, Char.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)

object AtomicNumberAnyLeftRight256JavaXSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight256, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue,
  allowPlatformIntrinsics = false, allowUnsafe = false)
