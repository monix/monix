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
import scala.util.control.NonFatal

abstract class GenericAtomicSuite[T, R <: Atomic[T]]
  (builder: AtomicBuilder[T, R], strategy: PaddingStrategy, valueFromInt: Int => T, valueToInt: T => Int,
   allowPlatformIntrinsics: Boolean)
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial, strategy, allowPlatformIntrinsics)
  val zero = valueFromInt(0)
  val one = valueFromInt(1)
  val two = valueFromInt(2)

  test("should set()") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)
    r.set(one)
    assert(r.get == one)
  }

  test("should getAndSet()") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)
    val old = r.getAndSet(one)
    assert(old == zero)
    assert(r.get == one)
  }

  test("should compareAndSet()") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)

    assert(r.compareAndSet(zero, one))
    assert(r.get == one)
    assert(r.compareAndSet(one, zero))
    assertEquals(r.get, zero)
    assert(!r.compareAndSet(one, one))
    assertEquals(r.get, zero)
  }

  test("should transform with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)

    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assert(r.get == one)
    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assertEquals(r.get, two)
  }

  test("should transform with dirty function #1") {
    val r = Atomic(zero)
    r.transform {
      def increment(y: T): T = valueFromInt(valueToInt(y) + 1)
      x: T => increment(x)
    }
    assert(r.get == one)
  }

  test("should transform with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: T): T = valueFromInt(valueToInt(y) + 1)

    r.transform(increment)
    assert(r.get == one)
  }

  test("should transform with dirty function #3") {
    val r = Atomic(zero)
    r.transform { x =>
      try valueFromInt(valueToInt(x) + 1) catch {
        case NonFatal(ex) =>
          x
      }
    }
    assert(r.get == one)
  }

  test("should transform with dirty self") {
    val r = Atomic(zero)
    def atomic = r
    assertEquals(atomic.get, zero)

    atomic.transform(x => valueFromInt(valueToInt(x) + 1))
    assertEquals(atomic.get, one)
    atomic.transform(x => valueFromInt(valueToInt(x) + 1))
    assertEquals(atomic.get, two)
  }

  test("should transformAndGet with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)

    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == one)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == two)
    assertEquals(r.get, two)
  }

  test("should transformAndGet with dirty function #1") {
    val r = Atomic(zero)
    val result = r.transformAndGet {
      def increment(y: T): T = valueFromInt(valueToInt(y) + 1)
      x: T => increment(x)
    }
    assertEquals(result, one)
  }

  test("should transformAndGet with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: T): T = valueFromInt(valueToInt(y) + 1)

    val result = r.transformAndGet(increment)
    assertEquals(result, one)
  }

  test("should transformAndGet with dirty function #3") {
    val r = Atomic(zero)
    val result = r.transformAndGet { x =>
      try valueFromInt(valueToInt(x) + 1) catch {
        case NonFatal(ex) =>
          x
      }
    }
    assertEquals(result, one)
  }

  test("should transformAndGet with dirty self") {
    var inst = Atomic(zero)
    def r = inst
    assertEquals(r.get, zero)

    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == one)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == two)
    assertEquals(r.get, two)
  }

  test("should getAndTransform with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)

    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == zero)
    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == one)
    assertEquals(r.get, two)
  }

  test("should getAndTransform with dirty function #1") {
    val r = Atomic(zero)
    val result = r.getAndTransform {
      def increment(y: T): T = valueFromInt(valueToInt(y) + 1)
      x: T => increment(x)
    }
    assertEquals(result, zero)
    assertEquals(r.get, one)
  }

  test("should getAndTransform with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: T): T = valueFromInt(valueToInt(y) + 1)

    val result = r.getAndTransform(increment)
    assertEquals(result, zero)
    assertEquals(r.get, one)
  }

  test("should getAndTransform with dirty function #3") {
    val r = Atomic(zero)
    val result = r.getAndTransform { x =>
      try valueFromInt(valueToInt(x) + 1) catch {
        case NonFatal(ex) =>
          x
      }
    }
    assertEquals(result, zero)
    assertEquals(r.get, one)
  }

  test("should getAndTransform with dirty self") {
    var inst = Atomic(zero)
    def r = inst
    assertEquals(r.get, zero)

    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == zero)
    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == one)
    assertEquals(r.get, two)
  }

  // --
  test("should transformAndExtract with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)

    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == zero)
    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == one)
    assertEquals(r.get, two)
  }


  test("should transformAndExtract with dirty function #1") {
    val r = Atomic(zero)
    val result = r.transformAndExtract {
      def increment(y: T): T = valueFromInt(valueToInt(y) + 1)
      x: T => (x, increment(x))
    }

    assertEquals(result, zero)
    assertEquals(r.get, one)
  }

  test("should transformAndExtract with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: T) = (y, valueFromInt(valueToInt(y) + 1))

    val result = r.transformAndExtract(increment)
    assertEquals(result, zero)
    assertEquals(r.get, one)
  }

  test("should transformAndExtract with dirty function #3") {
    val r = Atomic(zero)
    val result = r.transformAndExtract { x =>
      try { (x, valueFromInt(valueToInt(x) + 1)) } catch {
        case NonFatal(ex) =>
          (x, x)
      }
    }

    assertEquals(result, zero)
    assertEquals(r.get, one)
  }

  test("should transformAndExtract with dirty self") {
    var inst = Atomic(zero)
    def r = inst
    assertEquals(r.get, zero)

    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == zero)
    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == one)
    assertEquals(r.get, two)
  }

  test("should lazySet") {
    val r = Atomic(zero)
    assertEquals(r.get, zero)
    r.lazySet(one)
    assertEquals(r.get, one)
  }
}

// -- NoPadding (Java 8)

object GenericAtomicAnyNoPadding extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), NoPadding, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanNoPadding extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), NoPadding, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyNoPadding extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], NoPadding, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatNoPadding extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), NoPadding, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleNoPadding extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), NoPadding, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortNoPadding extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteNoPadding extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharNoPadding extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntNoPadding extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongNoPadding extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), NoPadding, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Left64 (Java 8)

object GenericAtomicAnyLeft64 extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeft64 extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Left64, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeft64 extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left64, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeft64 extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeft64 extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeft64 extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeft64 extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeft64 extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeft64 extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeft64 extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Right64 (Java 8)

object GenericAtomicAnyRight64 extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanRight64 extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Right64, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyRight64 extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right64, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatRight64 extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleRight64 extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortRight64 extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteRight64 extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharRight64 extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntRight64 extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongRight64 extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- LeftRight128 (Java 8)

object GenericAtomicAnyLeftRight128 extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeftRight128 extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), LeftRight128, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeftRight128 extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight128, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeftRight128 extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeftRight128 extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeftRight128 extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeftRight128 extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeftRight128 extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeftRight128 extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeftRight128 extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Left128 (Java 8)

object GenericAtomicAnyLeft128 extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeft128 extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Left128, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeft128 extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left128, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeft128 extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeft128 extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeft128 extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeft128 extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeft128 extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeft128 extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeft128 extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Right128 (Java 8)

object GenericAtomicAnyRight128 extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanRight128 extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Right128, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyRight128 extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right128, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatRight128 extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleRight128 extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortRight128 extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteRight128 extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharRight128 extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntRight128 extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongRight128 extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- LeftRight256 (Java 8)

object GenericAtomicAnyLeftRight256 extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight256, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeftRight256 extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), LeftRight256, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeftRight256 extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight256, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeftRight256 extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight256, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeftRight256 extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight256, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeftRight256 extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeftRight256 extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeftRight256 extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeftRight256 extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeftRight256 extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight256, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// ----------------- Java 7

// -- NoPadding (Java 7)

object GenericAtomicAnyNoPaddingJava7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), NoPadding, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanNoPaddingJava7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), NoPadding, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyNoPaddingJava7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], NoPadding, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatNoPaddingJava7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), NoPadding, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleNoPaddingJava7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), NoPadding, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortNoPaddingJava7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteNoPaddingJava7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharNoPaddingJava7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntNoPaddingJava7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongNoPaddingJava7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), NoPadding, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Left64 (Java 7)

object GenericAtomicAnyLeft64Java7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeft64Java7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Left64, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeft64Java7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left64, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeft64Java7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeft64Java7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeft64Java7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeft64Java7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeft64Java7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeft64Java7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeft64Java7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Right64 (Java 7)

object GenericAtomicAnyRight64Java7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanRight64Java7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Right64, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyRight64Java7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right64, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatRight64Java7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleRight64Java7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortRight64Java7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteRight64Java7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharRight64Java7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntRight64Java7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongRight64Java7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- LeftRight128 (Java 7)

object GenericAtomicAnyLeftRight128Java7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeftRight128Java7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), LeftRight128, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeftRight128Java7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight128, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeftRight128Java7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeftRight128Java7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeftRight128Java7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeftRight128Java7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeftRight128Java7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeftRight128Java7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeftRight128Java7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Left128 (Java 7)

object GenericAtomicAnyLeft128Java7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeft128Java7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Left128, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeft128Java7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Left128, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeft128Java7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeft128Java7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeft128Java7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeft128Java7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeft128Java7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeft128Java7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeft128Java7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Right128 (Java 7)

object GenericAtomicAnyRight128Java7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanRight128Java7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Right128, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyRight128Java7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], Right128, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatRight128Java7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleRight128Java7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortRight128Java7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteRight128Java7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharRight128Java7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntRight128Java7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongRight128Java7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- LeftRight256 (Java 7)

object GenericAtomicAnyLeftRight256Java7Suite extends GenericAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight256, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicBooleanLeftRight256Java7Suite extends GenericAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), LeftRight256, x => if (x == 1) true else false, x => if (x) 1 else 0,
  allowPlatformIntrinsics = true)

object GenericAtomicNumberAnyLeftRight256Java7Suite extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
  AtomicBuilder.AtomicNumberBuilder[BoxedLong], LeftRight256, x => BoxedLong(x), x => x.value.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicFloatLeftRight256Java7Suite extends GenericAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight256, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicDoubleLeftRight256Java7Suite extends GenericAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight256, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicShortLeftRight256Java7Suite extends GenericAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicByteLeftRight256Java7Suite extends GenericAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicCharLeftRight256Java7Suite extends GenericAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object GenericAtomicIntLeftRight256Java7Suite extends GenericAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, x => x, x => x,
  allowPlatformIntrinsics = true)

object GenericAtomicLongLeftRight256Java7Suite extends GenericAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight256, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)
