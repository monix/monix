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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


abstract class ConcurrentAtomicSuite[T, R <: Atomic[T]]
  (builder: AtomicBuilder[T, R], strategy: PaddingStrategy, valueFromInt: Int => T, valueToInt: T => Int)
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial, strategy)
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
    Await.result(f, 10.seconds)
    assert(r.get == valueFromInt(500))
  }

  test("should perform concurrent getAndSet") {
    val r = Atomic(zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.getAndSet(valueFromInt(j))
    }

    val f = Future.sequence(futures)
    Await.result(f, 10.seconds)
    assert(r.get == valueFromInt(99))
  }
}

object ConcurrentAtomicAnyNoPaddingSuite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), NoPadding, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanNoPaddingSuite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), NoPadding, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyNoPaddingSuite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), NoPadding, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatNoPaddingSuite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), NoPadding, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleNoPaddingSuite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), NoPadding, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortNoPaddingSuite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), NoPadding, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteNoPaddingSuite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), NoPadding, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharNoPaddingSuite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), NoPadding, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntNoPaddingSuite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), NoPadding, x => x, x => x)

object ConcurrentAtomicLongNoPaddingSuite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), NoPadding, x => x.toLong, x => x.toInt)

// -- Left64

object ConcurrentAtomicAnyLeft64Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left64, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanLeft64Suite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Left64, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyLeft64Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Left64, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatLeft64Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left64, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleLeft64Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left64, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortLeft64Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left64, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteLeft64Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left64, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharLeft64Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left64, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntLeft64Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left64, x => x, x => x)

object ConcurrentAtomicLongLeft64Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left64, x => x.toLong, x => x.toInt)

// -- Right64

object ConcurrentAtomicAnyRight64Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right64, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanRight64Suite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Right64, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyRight64Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Right64, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatRight64Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right64, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleRight64Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right64, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortRight64Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right64, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteRight64Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right64, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharRight64Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right64, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntRight64Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right64, x => x, x => x)

object ConcurrentAtomicLongRight64Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right64, x => x.toLong, x => x.toInt)

// -- LeftRight128

object ConcurrentAtomicAnyLeftRight128Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight128, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanLeftRight128Suite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), LeftRight128, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyLeftRight128Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), LeftRight128, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatLeftRight128Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight128, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleLeftRight128Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight128, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortLeftRight128Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight128, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteLeftRight128Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight128, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharLeftRight128Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight128, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntLeftRight128Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight128, x => x, x => x)

object ConcurrentAtomicLongLeftRight128Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight128, x => x.toLong, x => x.toInt)

// -- Left128

object ConcurrentAtomicAnyLeft128Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Left128, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanLeft128Suite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Left128, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyLeft128Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Left128, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatLeft128Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Left128, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleLeft128Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Left128, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortLeft128Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Left128, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteLeft128Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Left128, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharLeft128Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Left128, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntLeft128Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Left128, x => x, x => x)

object ConcurrentAtomicLongLeft128Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Left128, x => x.toLong, x => x.toInt)

// -- Right128

object ConcurrentAtomicAnyRight128Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), Right128, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanRight128Suite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), Right128, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyRight128Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), Right128, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatRight128Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), Right128, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleRight128Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), Right128, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortRight128Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), Right128, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteRight128Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), Right128, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharRight128Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), Right128, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntRight128Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), Right128, x => x, x => x)

object ConcurrentAtomicLongRight128Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), Right128, x => x.toLong, x => x.toInt)

// -- LeftRight256

object ConcurrentAtomicAnyLeftRight256Suite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  Atomic.builderFor(""), LeftRight256, x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanLeftRight256Suite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  Atomic.builderFor(true), LeftRight256, x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnyLeftRight256Suite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  Atomic.builderFor(BigInt(0)), LeftRight256, x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatLeftRight256Suite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  Atomic.builderFor(0.0f), LeftRight256, x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleLeftRight256Suite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  Atomic.builderFor(0.toDouble), LeftRight256, x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortLeftRight256Suite extends ConcurrentAtomicSuite[Short, AtomicShort](
  Atomic.builderFor(0.toShort), LeftRight256, x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteLeftRight256Suite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  Atomic.builderFor(0.toByte), LeftRight256, x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharLeftRight256Suite extends ConcurrentAtomicSuite[Char, AtomicChar](
  Atomic.builderFor(0.toChar), LeftRight256, x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntLeftRight256Suite extends ConcurrentAtomicSuite[Int, AtomicInt](
  Atomic.builderFor(0), LeftRight256, x => x, x => x)

object ConcurrentAtomicLongLeftRight256Suite extends ConcurrentAtomicSuite[Long, AtomicLong](
  Atomic.builderFor(0.toLong), LeftRight256, x => x.toLong, x => x.toInt)
