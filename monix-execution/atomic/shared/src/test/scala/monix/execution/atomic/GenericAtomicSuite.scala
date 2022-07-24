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

import monix.execution.atomic.PaddingStrategy._
import munit.FunSuite

import scala.annotation.nowarn
import scala.util.control.NonFatal

abstract class GenericAtomicSuite[A, R <: Atomic[A]](
  builder: AtomicBuilder[A, R],
  strategy: PaddingStrategy,
  valueFromInt: Int => A,
  valueToInt: A => Int,
  allowPlatformIntrinsics: Boolean,
  allowUnsafe: Boolean
) extends FunSuite {

  def Atomic(initial: A): R = {
    if (allowUnsafe)
      builder.buildInstance(initial, strategy, allowPlatformIntrinsics)
    else
      builder.buildSafeInstance(initial, strategy)
  }

  val zero = valueFromInt(0)
  val one = valueFromInt(1)
  val two = valueFromInt(2)

  test("should set()") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)
    r.set(one)
    assert(r.get() == one)
  }

  test("should getAndSet()") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)
    val old = r.getAndSet(one)
    assert(old == zero)
    assert(r.get() == one)
  }

  test("should compareAndSet()") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)

    assert(r.compareAndSet(zero, one))
    assert(r.get() == one)
    assert(r.compareAndSet(one, zero))
    assertEquals(r.get(), zero)
    assert(!r.compareAndSet(one, one))
    assertEquals(r.get(), zero)
  }

  test("should transform with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)

    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assert(r.get() == one)
    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assertEquals(r.get(), two)
  }

  test("should transform with dirty function #1") {
    val r = Atomic(zero)
    r.transform {
      def increment(y: A): A = valueFromInt(valueToInt(y) + 1)
      (x: A) => increment(x)
    }
    assert(r.get() == one)
  }

  test("should transform with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: A): A = valueFromInt(valueToInt(y) + 1)

    r.transform(increment)
    assert(r.get() == one)
  }

  test("should transform with dirty function #3") {
    val r = Atomic(zero)
    r.transform { x =>
      try valueFromInt(valueToInt(x) + 1)
      catch {
        case ex if NonFatal(ex) =>
          x
      }
    }
    assert(r.get() == one)
  }

  test("should transform with dirty self") {
    val r = Atomic(zero)
    def atomic = r
    assertEquals(atomic.get(), zero)

    atomic.transform(x => valueFromInt(valueToInt(x) + 1))
    assertEquals(atomic.get(), one)
    atomic.transform(x => valueFromInt(valueToInt(x) + 1))
    assertEquals(atomic.get(), two)
  }

  test("should transformAndGet with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)

    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == one)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == two)
    assertEquals(r.get(), two)
  }

  test("should transformAndGet with dirty function #1") {
    val r = Atomic(zero)
    val result = r.transformAndGet {
      def increment(y: A): A = valueFromInt(valueToInt(y) + 1)
      (x: A) => increment(x)
    }
    assertEquals(result, one)
  }

  test("should transformAndGet with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: A): A = valueFromInt(valueToInt(y) + 1)

    val result = r.transformAndGet(increment)
    assertEquals(result, one)
  }

  test("should transformAndGet with dirty function #3") {
    val r = Atomic(zero)
    val result = r.transformAndGet { x =>
      try valueFromInt(valueToInt(x) + 1)
      catch {
        case ex if NonFatal(ex) =>
          x
      }
    }
    assertEquals(result, one)
  }

  test("should transformAndGet with dirty self") {
    @nowarn var inst = Atomic(zero)
    def r = inst

    assertEquals(r.get(), zero)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == one)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) == two)
    assertEquals(r.get(), two)
  }

  test("should getAndTransform with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)

    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == zero)
    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == one)
    assertEquals(r.get(), two)
  }

  test("should getAndTransform with dirty function #1") {
    val r = Atomic(zero)
    val result = r.getAndTransform {
      def increment(y: A): A = valueFromInt(valueToInt(y) + 1)
      (x: A) => increment(x)
    }
    assertEquals(result, zero)
    assertEquals(r.get(), one)
  }

  test("should getAndTransform with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: A): A = valueFromInt(valueToInt(y) + 1)

    val result = r.getAndTransform(increment)
    assertEquals(result, zero)
    assertEquals(r.get(), one)
  }

  test("should getAndTransform with dirty function #3") {
    val r = Atomic(zero)
    val result = r.getAndTransform { x =>
      try valueFromInt(valueToInt(x) + 1)
      catch {
        case ex if NonFatal(ex) =>
          x
      }
    }
    assertEquals(result, zero)
    assertEquals(r.get(), one)
  }

  test("should getAndTransform with dirty self") {
    @nowarn var inst = Atomic(zero)

    def r = inst
    assertEquals(r.get(), zero)

    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == zero)
    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) == one)
    assertEquals(r.get(), two)
  }

  // --
  test("should transformAndExtract with clean arguments") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)

    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == zero)
    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == one)
    assertEquals(r.get(), two)
  }

  test("should transformAndExtract with dirty function #1") {
    val r = Atomic(zero)
    val result = r.transformAndExtract {
      def increment(y: A): A = valueFromInt(valueToInt(y) + 1)
      (x: A) => (x, increment(x))
    }

    assertEquals(result, zero)
    assertEquals(r.get(), one)
  }

  test("should transformAndExtract with dirty function #2") {
    val r = Atomic(zero)
    def increment(y: A) = (y, valueFromInt(valueToInt(y) + 1))

    val result = r.transformAndExtract(increment)
    assertEquals(result, zero)
    assertEquals(r.get(), one)
  }

  test("should transformAndExtract with dirty function #3") {
    val r = Atomic(zero)
    val result = r.transformAndExtract { x =>
      try {
        (x, valueFromInt(valueToInt(x) + 1))
      } catch {
        case ex if NonFatal(ex) =>
          (x, x)
      }
    }

    assertEquals(result, zero)
    assertEquals(r.get(), one)
  }

  test("should transformAndExtract with dirty self") {
    @nowarn var inst = Atomic(zero)

    def r = inst
    assertEquals(r.get(), zero)

    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == zero)
    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) == one)
    assertEquals(r.get(), two)
  }

  test("should lazySet") {
    val r = Atomic(zero)
    assertEquals(r.get(), zero)
    r.lazySet(one)
    assertEquals(r.get(), one)
  }
}

// -- NoPadding (Java 8)

class GenericAtomicAnyNoPadding
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    NoPadding,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanNoPadding
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    NoPadding,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyNoPadding
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    NoPadding,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatNoPadding
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleNoPadding
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    NoPadding,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortNoPadding
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteNoPadding
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharNoPadding
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntNoPadding
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongNoPadding
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    NoPadding,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// -- Left64 (Java 8)

class GenericAtomicAnyLeft64
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeft64
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Left64,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeft64
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Left64,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatLeft64
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeft64
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortLeft64
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteLeft64
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharLeft64
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntLeft64
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongLeft64
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// -- Right64 (Java 8)

class GenericAtomicAnyRight64
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanRight64
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Right64,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyRight64
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Right64,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatRight64
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleRight64
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortRight64
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteRight64
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharRight64
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntRight64
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongRight64
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// -- LeftRight128 (Java 8)

class GenericAtomicAnyLeftRight128
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeftRight128
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    LeftRight128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeftRight128
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    LeftRight128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatLeftRight128
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeftRight128
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortLeftRight128
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteLeftRight128
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharLeftRight128
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntLeftRight128
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongLeftRight128
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// -- Left128 (Java 8)

class GenericAtomicAnyLeft128
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeft128
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Left128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeft128
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Left128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatLeft128
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeft128
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortLeft128
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteLeft128
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharLeft128
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntLeft128
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongLeft128
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// -- Right128 (Java 8)

class GenericAtomicAnyRight128
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanRight128
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Right128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyRight128
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Right128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatRight128
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleRight128
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortRight128
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteRight128
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharRight128
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntRight128
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongRight128
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// -- LeftRight256 (Java 8)

class GenericAtomicAnyLeftRight256
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight256,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeftRight256
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    LeftRight256,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeftRight256
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    LeftRight256,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicFloatLeftRight256
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeftRight256
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight256,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicShortLeftRight256
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicByteLeftRight256
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicCharLeftRight256
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicIntLeftRight256
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    x => x,
    x => x,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

class GenericAtomicLongLeftRight256
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight256,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true,
    allowUnsafe = true
  )

// ----------------- Java 7

// -- NoPadding (Java 7)

class GenericAtomicAnyNoPaddingJava7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    NoPadding,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanNoPaddingJava7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    NoPadding,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyNoPaddingJava7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    NoPadding,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatNoPaddingJava7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleNoPaddingJava7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    NoPadding,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortNoPaddingJava7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteNoPaddingJava7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharNoPaddingJava7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntNoPaddingJava7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongNoPaddingJava7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    NoPadding,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// -- Left64 (Java 7)

class GenericAtomicAnyLeft64Java7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeft64Java7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Left64,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeft64Java7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Left64,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatLeft64Java7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeft64Java7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortLeft64Java7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteLeft64Java7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharLeft64Java7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntLeft64Java7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongLeft64Java7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// -- Right64 (Java 7)

class GenericAtomicAnyRight64Java7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanRight64Java7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Right64,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyRight64Java7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Right64,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatRight64Java7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleRight64Java7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortRight64Java7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteRight64Java7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharRight64Java7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntRight64Java7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongRight64Java7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// -- LeftRight128 (Java 7)

class GenericAtomicAnyLeftRight128Java7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeftRight128Java7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    LeftRight128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeftRight128Java7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    LeftRight128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatLeftRight128Java7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeftRight128Java7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortLeftRight128Java7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteLeftRight128Java7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharLeftRight128Java7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntLeftRight128Java7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongLeftRight128Java7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// -- Left128 (Java 7)

class GenericAtomicAnyLeft128Java7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeft128Java7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Left128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeft128Java7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Left128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatLeft128Java7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeft128Java7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortLeft128Java7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteLeft128Java7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharLeft128Java7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntLeft128Java7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongLeft128Java7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// -- Right128 (Java 7)

class GenericAtomicAnyRight128Java7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanRight128Java7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Right128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyRight128Java7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Right128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatRight128Java7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleRight128Java7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortRight128Java7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteRight128Java7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharRight128Java7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntRight128Java7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongRight128Java7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// -- LeftRight256 (Java 7)

class GenericAtomicAnyLeftRight256Java7Suite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight256,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicBooleanLeftRight256Java7Suite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    LeftRight256,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicNumberAnyLeftRight256Java7Suite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    LeftRight256,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicFloatLeftRight256Java7Suite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicDoubleLeftRight256Java7Suite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight256,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicShortLeftRight256Java7Suite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicByteLeftRight256Java7Suite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicCharLeftRight256Java7Suite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicIntLeftRight256Java7Suite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

class GenericAtomicLongLeftRight256Java7Suite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight256,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = true
  )

// ----------------- Java X

// -- NoPadding (Java X)

class GenericAtomicAnyNoPaddingJavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    NoPadding,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanNoPaddingJavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    NoPadding,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyNoPaddingJavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    NoPadding,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatNoPaddingJavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleNoPaddingJavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    NoPadding,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortNoPaddingJavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteNoPaddingJavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharNoPaddingJavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntNoPaddingJavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongNoPaddingJavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    NoPadding,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

// -- Left64 (Java X)

class GenericAtomicAnyLeft64JavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanLeft64JavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Left64,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyLeft64JavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Left64,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatLeft64JavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleLeft64JavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortLeft64JavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteLeft64JavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharLeft64JavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntLeft64JavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongLeft64JavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

// -- Right64 (Java X)

class GenericAtomicAnyRight64JavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanRight64JavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Right64,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyRight64JavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Right64,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatRight64JavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleRight64JavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortRight64JavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteRight64JavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharRight64JavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntRight64JavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongRight64JavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

// -- LeftRight128 (Java X)

class GenericAtomicAnyLeftRight128JavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanLeftRight128JavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    LeftRight128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyLeftRight128JavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    LeftRight128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatLeftRight128JavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleLeftRight128JavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortLeftRight128JavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteLeftRight128JavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharLeftRight128JavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntLeftRight128JavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongLeftRight128JavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

// -- Left128 (Java X)

class GenericAtomicAnyLeft128JavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanLeft128JavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Left128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyLeft128JavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Left128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatLeft128JavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleLeft128JavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortLeft128JavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteLeft128JavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharLeft128JavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntLeft128JavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongLeft128JavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

// -- Right128 (Java X)

class GenericAtomicAnyRight128JavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanRight128JavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    Right128,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyRight128JavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    Right128,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatRight128JavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleRight128JavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortRight128JavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteRight128JavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharRight128JavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntRight128JavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongRight128JavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

// -- LeftRight256 (Java X)

class GenericAtomicAnyLeftRight256JavaXSuite
  extends GenericAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight256,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicBooleanLeftRight256JavaXSuite
  extends GenericAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    LeftRight256,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicNumberAnyLeftRight256JavaXSuite
  extends GenericAtomicSuite[BoxedLong, AtomicNumberAny[BoxedLong]](
    AtomicBuilder.AtomicNumberBuilder[BoxedLong],
    LeftRight256,
    x => BoxedLong(x.toLong),
    x => x.value.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicFloatLeftRight256JavaXSuite
  extends GenericAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicDoubleLeftRight256JavaXSuite
  extends GenericAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight256,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicShortLeftRight256JavaXSuite
  extends GenericAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicByteLeftRight256JavaXSuite
  extends GenericAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicCharLeftRight256JavaXSuite
  extends GenericAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicIntLeftRight256JavaXSuite
  extends GenericAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    x => x,
    x => x,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )

class GenericAtomicLongLeftRight256JavaXSuite
  extends GenericAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight256,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false,
    allowUnsafe = false
  )
