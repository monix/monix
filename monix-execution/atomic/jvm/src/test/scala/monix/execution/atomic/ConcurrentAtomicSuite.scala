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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

abstract class ConcurrentAtomicSuite[A, R <: Atomic[A]](
  builder: AtomicBuilder[A, R],
  strategy: PaddingStrategy,
  valueFromInt: Int => A,
  valueToInt: A => Int,
  allowPlatformIntrinsics: Boolean
) extends FunSuite {

  def Atomic(initial: A): R = builder.buildInstance(initial, strategy, allowPlatformIntrinsics)
  def zero = valueFromInt(0)
  def one = valueFromInt(1)
  def two = valueFromInt(2)

  test("should perform concurrent compareAndSet") {
    val r = Atomic(zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (_ <- 0 until 100)
          r.transform(x => valueFromInt(valueToInt(x) + 1))
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == valueFromInt(500))
  }

  test("should perform concurrent getAndSet") {
    val r = Atomic(zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (j <- 0 until 100)
          r.getAndSet(valueFromInt(j))
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == valueFromInt(99))
  }
}

abstract class ConcurrentAtomicBooleanSuite(strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean = true)
  extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
    Atomic.builderFor(true),
    strategy,
    x => if (x == 1) true else false,
    x => if (x) 1 else 0,
    allowPlatformIntrinsics
  ) {

  test("should flip to true when false") {
    val r = Atomic(false)
    val futures =
      for (_ <- 0 until 5) yield Future {
        r.flip(true)
      }
    val result = Await.result(Future.sequence(futures), 30.seconds)
    assert(result.count(_ == true) == 1)
    assert(r.get())
  }

  test("should not flip to true when already true") {
    val r = Atomic(true)
    val futures =
      for (_ <- 0 until 5) yield Future {
        r.flip(true)
      }
    val result = Await.result(Future.sequence(futures), 30.seconds)
    assert(result.forall(_ == false))
  }
}

// -- NoPadding (Java 8)

class ConcurrentAtomicAnyNoPaddingSuite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    NoPadding,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanNoPaddingSuite extends ConcurrentAtomicBooleanSuite(NoPadding)

class ConcurrentAtomicNumberAnyNoPaddingSuite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    NoPadding,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatNoPaddingSuite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleNoPaddingSuite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    NoPadding,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortNoPaddingSuite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteNoPaddingSuite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharNoPaddingSuite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntNoPaddingSuite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongNoPaddingSuite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    NoPadding,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -- Left64 (Java 8)

class ConcurrentAtomicAnyLeft64Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanLeft64Suite extends ConcurrentAtomicBooleanSuite(Left64)

class ConcurrentAtomicNumberAnyLeft64Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left64,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatLeft64Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleLeft64Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortLeft64Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteLeft64Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharLeft64Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntLeft64Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongLeft64Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -- Right64 (Java 8)

class ConcurrentAtomicAnyRight64Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanRight64Suite extends ConcurrentAtomicBooleanSuite(Right64)

class ConcurrentAtomicNumberAnyRight64Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right64,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatRight64Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleRight64Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortRight64Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteRight64Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharRight64Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntRight64Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongRight64Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -- LeftRight128 (Java 8)

class ConcurrentAtomicAnyLeftRight128Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanLeftRight128Suite extends ConcurrentAtomicBooleanSuite(LeftRight128)

class ConcurrentAtomicNumberAnyLeftRight128Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight128,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatLeftRight128Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleLeftRight128Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortLeftRight128Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteLeftRight128Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharLeftRight128Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntLeftRight128Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongLeftRight128Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -- Left128 (Java 8)

class ConcurrentAtomicAnyLeft128Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanLeft128Suite extends ConcurrentAtomicBooleanSuite(Left128)

class ConcurrentAtomicNumberAnyLeft128Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left128,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatLeft128Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleLeft128Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortLeft128Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteLeft128Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharLeft128Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntLeft128Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongLeft128Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -- Right128 (Java 8)

class ConcurrentAtomicAnyRight128Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanRight128Suite extends ConcurrentAtomicBooleanSuite(Right128)

class ConcurrentAtomicNumberAnyRight128Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right128,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatRight128Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleRight128Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortRight128Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteRight128Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharRight128Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntRight128Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongRight128Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -- LeftRight256 (Java 8)

class ConcurrentAtomicAnyLeftRight256Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight256,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicBooleanLeftRight256Suite extends ConcurrentAtomicBooleanSuite(LeftRight256)

class ConcurrentAtomicNumberAnyLeftRight256Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight256,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicFloatLeftRight256Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicDoubleLeftRight256Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight256,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicShortLeftRight256Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicByteLeftRight256Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicCharLeftRight256Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicIntLeftRight256Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    x => x,
    x => x,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicLongLeftRight256Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight256,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = true
  )

// -------------- Java 7

// -- NoPadding (Java 7)

class ConcurrentAtomicAnyNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    NoPadding,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanNoPaddingJava7Suite
  extends ConcurrentAtomicBooleanSuite(NoPadding, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    NoPadding,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    NoPadding,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongNoPaddingJava7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    NoPadding,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

// -- Left64 (Java 7)

class ConcurrentAtomicAnyLeft64Java7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanLeft64Java7Suite
  extends ConcurrentAtomicBooleanSuite(Left64, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyLeft64Java7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left64,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatLeft64Java7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleLeft64Java7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortLeft64Java7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteLeft64Java7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharLeft64Java7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntLeft64Java7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongLeft64Java7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

// -- Right64 (Java 7)

class ConcurrentAtomicAnyRight64Java7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right64,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanRight64Java7Suite
  extends ConcurrentAtomicBooleanSuite(Right64, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyRight64Java7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right64,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatRight64Java7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleRight64Java7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right64,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortRight64Java7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteRight64Java7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharRight64Java7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntRight64Java7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongRight64Java7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right64,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

// -- LeftRight128 (Java 7)

class ConcurrentAtomicAnyLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanLeftRight128Java7Suite
  extends ConcurrentAtomicBooleanSuite(LeftRight128, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight128,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongLeftRight128Java7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

// -- Left128 (Java 7)

class ConcurrentAtomicAnyLeft128Java7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Left128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanLeft128Java7Suite
  extends ConcurrentAtomicBooleanSuite(Left128, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyLeft128Java7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left128,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatLeft128Java7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleLeft128Java7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Left128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortLeft128Java7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteLeft128Java7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharLeft128Java7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntLeft128Java7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongLeft128Java7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Left128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

// -- Right128 (Java 7)

class ConcurrentAtomicAnyRight128Java7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    Right128,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanRight128Java7Suite
  extends ConcurrentAtomicBooleanSuite(Right128, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyRight128Java7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right128,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatRight128Java7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleRight128Java7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    Right128,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortRight128Java7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteRight128Java7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharRight128Java7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntRight128Java7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongRight128Java7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    Right128,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

// -- LeftRight256 (Java 7)

class ConcurrentAtomicAnyLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[String, AtomicAny[String]](
    Atomic.builderFor(""),
    LeftRight256,
    x => x.toString,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicBooleanLeftRight256Java7Suite
  extends ConcurrentAtomicBooleanSuite(LeftRight256, allowPlatformIntrinsics = false)

class ConcurrentAtomicNumberAnyLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight256,
    x => BigInt(x),
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicFloatLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    x => x.toFloat,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicDoubleLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Double, AtomicDouble](
    Atomic.builderFor(0.toDouble),
    LeftRight256,
    x => x.toDouble,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicShortLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    x => x.toShort,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicByteLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    x => x.toByte,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicCharLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    x => x.toChar,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicIntLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    x => x,
    x => x,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicLongLeftRight256Java7Suite
  extends ConcurrentAtomicSuite[Long, AtomicLong](
    Atomic.builderFor(0.toLong),
    LeftRight256,
    x => x.toLong,
    x => x.toInt,
    allowPlatformIntrinsics = false
  )
