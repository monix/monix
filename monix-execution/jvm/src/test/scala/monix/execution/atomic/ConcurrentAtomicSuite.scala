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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


abstract class ConcurrentAtomicSuite[A, R <: Atomic[A]](
  builder: AtomicBuilder[A, R], strategy: PaddingStrategy,
  valueFromInt: Int => A, valueToInt: A => Int,
  allowPlatformIntrinsics: Boolean)
  extends SimpleTestSuite {

  def Atomic(initial: A): R = builder.buildInstance(initial, strategy, allowPlatformIntrinsics)
  def zero = valueFromInt(0)
  def one = valueFromInt(1)
  def two = valueFromInt(2)

  test("should perform concurrent compareAndSet") {
    val r = Atomic(zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.transform(x => valueFromInt(valueToInt(x) + 1))
    }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get == valueFromInt(500))
  }

  test("should perform concurrent getAndSet") {
    val r = Atomic(zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.getAndSet(valueFromInt(j))
    }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get == valueFromInt(99))
  }
}

abstract class ConcurrentAtomicBooleanSuite(strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean = true)
  extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](Atomic.builderFor(true),
    strategy, x => if (x == 1) true else false, x => if (x) 1 else 0, allowPlatformIntrinsics) {

  test("should flip to true when false") {
    val r = Atomic(false)
    val futures = for (_ <- 0 until 5) yield Future {
      r.flip(true)
    }
    val result = Await.result(Future.sequence(futures), 30.seconds)
    assert(result.count(_ == true) == 1)
    assert(r.get)
  }

  test("should not flip to true when already true") {
    val r = Atomic(true)
    val futures = for (_ <- 0 until 5) yield Future {
      r.flip(true)
    }
    val result = Await.result(Future.sequence(futures), 30.seconds)
    assert(result.forall(_ == false))
  }
}

// -- NoPadding (Java 8)

object ConcurrentAtomicAnyNoPaddingSuite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), NoPadding, x => x.toString, x => x.toInt, allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanNoPaddingSuite extends  ConcurrentAtomicBooleanSuite(NoPadding)

object ConcurrentAtomicNumberAnyNoPaddingSuite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), NoPadding, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatNoPaddingSuite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), NoPadding, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleNoPaddingSuite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), NoPadding, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortNoPaddingSuite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteNoPaddingSuite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharNoPaddingSuite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntNoPaddingSuite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongNoPaddingSuite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), NoPadding, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Left64 (Java 8)

object ConcurrentAtomicAnyLeft64Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanLeft64Suite extends ConcurrentAtomicBooleanSuite(Left64)

object ConcurrentAtomicNumberAnyLeft64Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Left64, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatLeft64Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleLeft64Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortLeft64Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteLeft64Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharLeft64Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntLeft64Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongLeft64Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Right64 (Java 8)

object ConcurrentAtomicAnyRight64Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanRight64Suite extends ConcurrentAtomicBooleanSuite(Right64)

object ConcurrentAtomicNumberAnyRight64Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Right64, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatRight64Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleRight64Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortRight64Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteRight64Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharRight64Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntRight64Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongRight64Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- LeftRight128 (Java 8)

object ConcurrentAtomicAnyLeftRight128Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanLeftRight128Suite extends ConcurrentAtomicBooleanSuite(LeftRight128)

object ConcurrentAtomicNumberAnyLeftRight128Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), LeftRight128, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatLeftRight128Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleLeftRight128Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortLeftRight128Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteLeftRight128Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharLeftRight128Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntLeftRight128Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongLeftRight128Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Left128 (Java 8)

object ConcurrentAtomicAnyLeft128Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanLeft128Suite extends ConcurrentAtomicBooleanSuite(Left128)

object ConcurrentAtomicNumberAnyLeft128Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Left128, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatLeft128Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleLeft128Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortLeft128Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteLeft128Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharLeft128Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntLeft128Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongLeft128Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- Right128 (Java 8)

object ConcurrentAtomicAnyRight128Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanRight128Suite extends ConcurrentAtomicBooleanSuite(Right128)

object ConcurrentAtomicNumberAnyRight128Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Right128, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatRight128Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleRight128Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortRight128Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteRight128Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharRight128Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntRight128Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongRight128Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -- LeftRight256 (Java 8)

object ConcurrentAtomicAnyLeftRight256Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight256, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicBooleanLeftRight256Suite extends ConcurrentAtomicBooleanSuite(LeftRight256)

object ConcurrentAtomicNumberAnyLeftRight256Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), LeftRight256, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicFloatLeftRight256Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight256, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicDoubleLeftRight256Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight256, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicShortLeftRight256Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicByteLeftRight256Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicCharLeftRight256Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicIntLeftRight256Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, x => x, x => x,
  allowPlatformIntrinsics = true)

object ConcurrentAtomicLongLeftRight256Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight256, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = true)

// -------------- Java 7

// -- NoPadding (Java 7)

object ConcurrentAtomicAnyNoPaddingJava7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), NoPadding, x => x.toString, x => x.toInt, allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanNoPaddingJava7Suite extends  ConcurrentAtomicBooleanSuite(
  NoPadding, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyNoPaddingJava7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), NoPadding, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatNoPaddingJava7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), NoPadding, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleNoPaddingJava7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), NoPadding, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortNoPaddingJava7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteNoPaddingJava7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharNoPaddingJava7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntNoPaddingJava7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongNoPaddingJava7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), NoPadding, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)

// -- Left64 (Java 7)

object ConcurrentAtomicAnyLeft64Java7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanLeft64Java7Suite extends  ConcurrentAtomicBooleanSuite(
  Left64, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyLeft64Java7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Left64, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatLeft64Java7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleLeft64Java7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortLeft64Java7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteLeft64Java7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharLeft64Java7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntLeft64Java7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongLeft64Java7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)

// -- Right64 (Java 7)

object ConcurrentAtomicAnyRight64Java7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right64, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanRight64Java7Suite extends ConcurrentAtomicBooleanSuite(
  Right64, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyRight64Java7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Right64, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatRight64Java7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right64, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleRight64Java7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right64, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortRight64Java7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteRight64Java7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharRight64Java7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntRight64Java7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongRight64Java7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right64, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)

// -- LeftRight128 (Java 7)

object ConcurrentAtomicAnyLeftRight128Java7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanLeftRight128Java7Suite extends ConcurrentAtomicBooleanSuite(
  LeftRight128, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyLeftRight128Java7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), LeftRight128, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatLeftRight128Java7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleLeftRight128Java7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortLeftRight128Java7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteLeftRight128Java7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharLeftRight128Java7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntLeftRight128Java7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongLeftRight128Java7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)

// -- Left128 (Java 7)

object ConcurrentAtomicAnyLeft128Java7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanLeft128Java7Suite extends ConcurrentAtomicBooleanSuite(
  Left128, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyLeft128Java7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Left128, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatLeft128Java7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleLeft128Java7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortLeft128Java7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteLeft128Java7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharLeft128Java7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntLeft128Java7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongLeft128Java7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)

// -- Right128 (Java 7)

object ConcurrentAtomicAnyRight128Java7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right128, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanRight128Java7Suite extends ConcurrentAtomicBooleanSuite(
  Right128, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyRight128Java7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Right128, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatRight128Java7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right128, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleRight128Java7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right128, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortRight128Java7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteRight128Java7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharRight128Java7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntRight128Java7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongRight128Java7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right128, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)

// -- LeftRight256 (Java 7)

object ConcurrentAtomicAnyLeftRight256Java7Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight256, x => x.toString, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicBooleanLeftRight256Java7Suite extends ConcurrentAtomicBooleanSuite(
  LeftRight256, allowPlatformIntrinsics = false)

object ConcurrentAtomicNumberAnyLeftRight256Java7Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), LeftRight256, x => BigInt(x), x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicFloatLeftRight256Java7Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight256, x => x.toFloat, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicDoubleLeftRight256Java7Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight256, x => x.toDouble, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicShortLeftRight256Java7Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, x => x.toShort, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicByteLeftRight256Java7Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, x => x.toByte, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicCharLeftRight256Java7Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, x => x.toChar, x => x.toInt,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicIntLeftRight256Java7Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, x => x, x => x,
  allowPlatformIntrinsics = false)

object ConcurrentAtomicLongLeftRight256Java7Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight256, x => x.toLong, x => x.toInt,
  allowPlatformIntrinsics = false)
