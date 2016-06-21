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

import minitest.SimpleTestSuite
import monix.execution.atomic.PaddingStrategy._

abstract class AtomicNumberSuite[T, R <: AtomicNumber[T]]
  (builder: AtomicBuilder[T, R], strategy: PaddingStrategy,
  value: T, maxValue: T, minValue: T)(implicit ev: Numeric[T])
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial, strategy)
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

  test("should incrementAndGet()") {
    val r = Atomic(value)
    assert(r.incrementAndGet() == ev.plus(value, one))
    assert(r.incrementAndGet() == ev.plus(value, ev.plus(one, one)))
  }

  test("should incrementAndGet(value)") {
    val r = Atomic(value)
    assert(r.incrementAndGet(ev.toInt(value)) == ev.plus(value, ev.fromInt(ev.toInt(value))))
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

  test("should add(value)") {
    val r = Atomic(value)
    r.add(value)
    assert(r.get == ev.plus(value, value))
    r.add(value)
    assert(r.get == ev.plus(value, ev.plus(value, value)))
  }


  test("should getAndAdd(value)") {
    val r = Atomic(value)
    assert(r.getAndAdd(value) == value)
    assert(r.get == ev.plus(value, value))
  }

  test("should subtractAndGet(value)") {
    val r = Atomic(value)
    assert(r.subtractAndGet(value) == ev.minus(value, value))
    assert(r.subtractAndGet(value) == ev.minus(value, ev.plus(value, value)))
  }

  test("should subtract(value)") {
    val r = Atomic(value)
    r.subtract(value)
    assert(r.get == ev.minus(value, value))
    r.subtract(value)
    assert(r.get == ev.minus(value, ev.plus(value, value)))
  }

  test("should getAndSubtract(value)") {
    val r = Atomic(value)
    assert(r.getAndSubtract(value) == value)
    assert(r.get == zero)
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
    def fn(x: T): T = ev.plus(x, x)
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
    def fn(x: T) = ev.plus(x,x)
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
    def fn(x: T) = ev.plus(x,x)
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

abstract class AtomicDoubleSuite(strategy: PaddingStrategy) extends AtomicNumberSuite[Double, AtomicDouble](
  Atomic.builderFor(0.0), strategy, 17.23, Double.MaxValue, Double.MinValue) {

  test("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
    assert(Atomic(Double.MinPositiveValue).get == Double.MinPositiveValue)
    assert(Atomic(Double.NaN).get.isNaN)
    assert(Atomic(Double.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Double.PositiveInfinity).get.isPosInfinity)
  }
}

abstract class AtomicFloatSuite(strategy: PaddingStrategy) extends AtomicNumberSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), strategy, 17.23f, Float.MaxValue, Float.MinValue) {

  test("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
    assert(Atomic(Float.MinPositiveValue).get == Float.MinPositiveValue)
    assert(Atomic(Float.NaN).get.isNaN)
    assert(Atomic(Float.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Float.PositiveInfinity).get.isPosInfinity)
  }
}

// -- NoPadding

object AtomicDoubleNoPaddingSuite extends AtomicDoubleSuite(NoPadding)
object AtomicFloatNoPaddingSuite extends AtomicFloatSuite(NoPadding)

object AtomicLongNoPaddingSuite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), NoPadding, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntNoPaddingSuite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, 782, Int.MaxValue, Int.MinValue)

object AtomicShortNoPaddingSuite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteNoPaddingSuite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharNoPaddingSuite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyNoPaddingSuite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], NoPadding, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)

// -- Left64

object AtomicDoubleLeft64Suite extends AtomicDoubleSuite(Left64)
object AtomicFloatLeft64Suite extends AtomicFloatSuite(Left64)

object AtomicLongLeft64Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left64, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntLeft64Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, 782, Int.MaxValue, Int.MinValue)

object AtomicShortLeft64Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteLeft64Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharLeft64Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyLeft64Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)

// -- Right64

object AtomicDoubleRight64Suite extends AtomicDoubleSuite(Right64)
object AtomicFloatRight64Suite extends AtomicFloatSuite(Right64)

object AtomicLongRight64Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right64, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntRight64Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, 782, Int.MaxValue, Int.MinValue)

object AtomicShortRight64Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteRight64Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharRight64Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyRight64Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right64, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)

// -- LeftRight128

object AtomicDoubleLeftRight128Suite extends AtomicDoubleSuite(LeftRight128)
object AtomicFloatLeftRight128Suite extends AtomicFloatSuite(LeftRight128)

object AtomicLongLeftRight128Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight128, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntLeftRight128Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, 782, Int.MaxValue, Int.MinValue)

object AtomicShortLeftRight128Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteLeftRight128Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharLeftRight128Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyLeftRight128Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)

// -- Left128

object AtomicDoubleLeft128Suite extends AtomicDoubleSuite(Left128)
object AtomicFloatLeft128Suite extends AtomicFloatSuite(Left128)

object AtomicLongLeft128Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Left128, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntLeft128Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, 782, Int.MaxValue, Int.MinValue)

object AtomicShortLeft128Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteLeft128Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharLeft128Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyLeft128Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)

// -- Right128

object AtomicDoubleRight128Suite extends AtomicDoubleSuite(Right128)
object AtomicFloatRight128Suite extends AtomicFloatSuite(Right128)

object AtomicLongRight128Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), Right128, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntRight128Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, 782, Int.MaxValue, Int.MinValue)

object AtomicShortRight128Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteRight128Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharRight128Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyRight128Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right128, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)

// -- LeftRight256

object AtomicDoubleLeftRight256Suite extends AtomicDoubleSuite(LeftRight256)
object AtomicFloatLeftRight256Suite extends AtomicFloatSuite(LeftRight256)

object AtomicLongLeftRight256Suite extends AtomicNumberSuite[Long, AtomicLong](
  Atomic.builderFor(0L), LeftRight256, -782L, Long.MaxValue, Long.MinValue)

object AtomicIntLeftRight256Suite extends AtomicNumberSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, 782, Int.MaxValue, Int.MinValue)

object AtomicShortLeftRight256Suite extends AtomicNumberSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteLeftRight256Suite extends AtomicNumberSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharLeftRight256Suite extends AtomicNumberSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnyLeftRight256Suite extends AtomicNumberSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight256, BoxedLong(782), BoxedLong.MaxValue, BoxedLong.MinValue)
