/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

package monix.concurrent.atomic

import minitest.SimpleTestSuite

abstract class AtomicNumberSuite[T, R <: AtomicNumber[T]]
  (name: String, builder: AtomicBuilder[T, R],
  value: T, maxValue: T, minValue: T)(implicit ev: Numeric[T])
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial)

  val two = ev.plus(ev.one, ev.one)

  test("should get()") {
    assert(Atomic(value).get == value)
    assert(Atomic(maxValue).get == maxValue)
    assert(Atomic(minValue).get == minValue)
  }

  test("should set()") {
    val r = Atomic(ev.zero)
    r.set(value)
    assert(r.get == value)
    r.set(minValue)
    assert(r.get == minValue)
    r.set(maxValue)
    assert(r.get == maxValue)
  }

  test("should compareAndSet()") {
    val r = Atomic(ev.zero)
    assert(r.compareAndSet(ev.zero, ev.one))
    assert(!r.compareAndSet(ev.zero, ev.one))

    assert(r.get == ev.one)
  }

  test("should getAndSet()") {
    val r = Atomic(ev.zero)
    assert(r.getAndSet(ev.one) == ev.zero)
    assert(r.getAndSet(value) == ev.one)
    assert(r.getAndSet(minValue) == value)
    assert(r.getAndSet(maxValue) == minValue)
    assert(r.get == maxValue)
  }

  test("should increment()") {
    val r = Atomic(value)
    r.increment()
    assert(r.get == ev.plus(value, ev.one))
    r.increment()
    assert(r.get == ev.plus(value, ev.plus(ev.one, ev.one)))
  }

  test("should increment(value)") {
    val r = Atomic(value)
    r.increment(ev.toInt(value))
    assert(r.get == ev.plus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should decrement()") {
    val r = Atomic(value)
    r.decrement()
    assert(r.get == ev.minus(value, ev.one))
    r.decrement()
    assert(r.get == ev.minus(value, ev.plus(ev.one, ev.one)))
  }

  test("should decrement(value)") {
    val r = Atomic(value)
    r.decrement(ev.toInt(value))
    assert(r.get == ev.minus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should incrementAndGet()") {
    val r = Atomic(value)
    assert(r.incrementAndGet() == ev.plus(value, ev.one))
    assert(r.incrementAndGet() == ev.plus(value, ev.plus(ev.one, ev.one)))
  }

  test("should incrementAndGet(value)") {
    val r = Atomic(value)
    assert(r.incrementAndGet(ev.toInt(value)) == ev.plus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should decrementAndGet()") {
    val r = Atomic(value)
    assert(r.decrementAndGet() == ev.minus(value, ev.one))
    assert(r.decrementAndGet() == ev.minus(value, ev.plus(ev.one, ev.one)))
  }

  test("should decrementAndGet(value)") {
    val r = Atomic(value)
    assert(r.decrementAndGet(ev.toInt(value)) == ev.minus(value, ev.fromInt(ev.toInt(value))))
  }

  test("should getAndIncrement()") {
    val r = Atomic(value)
    assert(r.getAndIncrement() == value)
    assert(r.getAndIncrement() == ev.plus(value, ev.one))
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
    assert(r.getAndDecrement() == ev.minus(value, ev.one))
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

  test("should getAndSubtract(value)") {
    val r = Atomic(value)
    assert(r.getAndSubtract(value) == value)
    assert(r.get == ev.zero)
  }

  test("should transform()") {
    val r = Atomic(value)
    r.transform(x => ev.plus(x, x))
    assert(r.get == ev.plus(value, value))
  }

  test("should transformAndGet()") {
    val r = Atomic(value)
    assert(r.transformAndGet(x => ev.plus(x, x)) == ev.plus(value, value))
  }

  test("should getAndTransform()") {
    val r = Atomic(value)
    assert(r.getAndTransform(x => ev.plus(x, x)) == value)
    assert(r.get == ev.plus(value, value))
  }

  test("should maybe overflow on max") {
    val r = Atomic(maxValue)
    r.increment()
    assert(r.get == ev.plus(maxValue, ev.one))
  }

  test("should maybe overflow on min") {
    val r = Atomic(minValue)
    r.decrement()
    assert(r.get == ev.minus(minValue, ev.one))
  }
}

object AtomicDoubleSuite extends AtomicNumberSuite[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.0), 17.23, Double.MaxValue, Double.MinValue) {

  test("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
    assert(Atomic(Double.MinPositiveValue).get == Double.MinPositiveValue)
    assert(Atomic(Double.NaN).get.isNaN)
    assert(Atomic(Double.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Double.PositiveInfinity).get.isPosInfinity)
  }
}

object AtomicFloatSuite extends AtomicNumberSuite[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), 17.23f, Float.MaxValue, Float.MinValue) {

  test("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
    assert(Atomic(Float.MinPositiveValue).get == Float.MinPositiveValue)
    assert(Atomic(Float.NaN).get.isNaN)
    assert(Atomic(Float.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Float.PositiveInfinity).get.isPosInfinity)
  }
}

object AtomicLongSuite extends AtomicNumberSuite[Long, AtomicLong](
  "AtomicLong", Atomic.builderFor(0L), -782L, Long.MaxValue, Long.MinValue)

object AtomicIntSuite extends AtomicNumberSuite[Int, AtomicInt](
  "AtomicInt", Atomic.builderFor(0), 782, Int.MaxValue, Int.MinValue)

object AtomicShortSuite extends AtomicNumberSuite[Short, AtomicShort](
  "AtomicShort", Atomic.builderFor(0.toShort), 782.toShort, Short.MaxValue, Short.MinValue)

object AtomicByteSuite extends AtomicNumberSuite[Byte, AtomicByte](
  "AtomicByte", Atomic.builderFor(0.toByte), 782.toByte, Byte.MaxValue, Byte.MinValue)

object AtomicCharSuite extends AtomicNumberSuite[Char, AtomicChar](
  "AtomicChar", Atomic.builderFor(0.toChar), 782.toChar, Char.MaxValue, Char.MinValue)

object AtomicNumberAnySuite extends AtomicNumberSuite[Long, AtomicNumberAny[Long]](
  "AtomicNumberAny", AtomicBuilder.AtomicNumberBuilder[Long], 782, Long.MaxValue, Long.MinValue)
