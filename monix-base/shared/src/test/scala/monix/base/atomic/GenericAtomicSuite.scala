/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
 *
 */

package monix.base.atomic

import minitest.SimpleTestSuite

abstract class GenericAtomicSuite[T, R <: Atomic[T]]
  (name: String, builder: AtomicBuilder[T, R], valueFromInt: Int => T, valueToInt: T => Int)
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial)

  def zero = valueFromInt(0)
  def one = valueFromInt(1)
  def two = valueFromInt(2)

  test("should set()") {
    val r = Atomic(zero)
    assert(r.get == zero)
    r.set(one)
    assert(r.get == one)
  }

  test("should getAndSet()") {
    val r = Atomic(zero)
    assert(r.get == zero)
    val old = r.getAndSet(one)
    assert(old == zero)
    assert(r.get == one)
  }

  test("should compareAndSet()") {
    val r = Atomic(zero)
    assert(r.get == zero)

    assert(r.compareAndSet(zero, one))
    assert(r.get == one)
    assert(r.compareAndSet(one, zero))
    assert(r.get == zero)
    assert(!r.compareAndSet(one, one))
    assert(r.get == zero)
  }

  test("should transform") {
    val r = Atomic(zero)
    assert(r.get == zero)

    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assert(r.get == one)
    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assert(r.get == two)
  }

  test("should transformAndGet") {
    val r = Atomic(zero)
    assert(r.get == zero)

    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == one)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == two)
    assert(r.get == two)
  }

  test("should getAndTransform") {
    val r = Atomic(zero)
    assert(r.get == zero)

    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == zero)
    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == one)
    assert(r.get == two)
  }

  test("should transformAndExtract") {
    val r = Atomic(zero)
    assert(r.get == zero)

    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == zero)
    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == one)
    assert(r.get == two)
  }
}

object GenericAtomicAnySuite extends GenericAtomicSuite[String, AtomicAny[String]](
  "AtomicAny", Atomic.builderFor(""), x => x.toString, x => x.toInt)

object GenericAtomicBooleanSuite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  "AtomicBoolean", Atomic.builderFor(true), x => if (x == 1) true else false, x => if (x) 1 else 0)

object GenericAtomicNumberAnySuite extends GenericAtomicSuite[Long, AtomicNumberAny[Long]](
  "AtomicNumberAny", AtomicBuilder.AtomicNumberBuilder[Long], x => x.toLong, x => x.toInt)

object GenericAtomicFloatSuite extends GenericAtomicSuite[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), x => x.toFloat, x => x.toInt)

object GenericAtomicDoubleSuite extends GenericAtomicSuite[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.toDouble), x => x.toDouble, x => x.toInt)

object GenericAtomicShortSuite extends GenericAtomicSuite[Short, AtomicShort](
  "AtomicShort", Atomic.builderFor(0.toShort), x => x.toShort, x => x.toInt)

object GenericAtomicByteSuite extends GenericAtomicSuite[Byte, AtomicByte](
  "AtomicByte", Atomic.builderFor(0.toByte), x => x.toByte, x => x.toInt)

object GenericAtomicCharSuite extends GenericAtomicSuite[Char, AtomicChar](
  "AtomicChar", Atomic.builderFor(0.toChar), x => x.toChar, x => x.toInt)

object GenericAtomicIntSuite extends GenericAtomicSuite[Int, AtomicInt](
  "AtomicInt", Atomic.builderFor(0), x => x, x => x)

object GenericAtomicLongSuite extends GenericAtomicSuite[Long, AtomicLong](
  "AtomicLong", Atomic.builderFor(0.toLong), x => x.toLong, x => x.toInt)
