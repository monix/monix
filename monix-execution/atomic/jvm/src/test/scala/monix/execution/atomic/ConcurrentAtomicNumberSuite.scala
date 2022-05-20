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

import minitest.SimpleTestSuite
import monix.execution.atomic.PaddingStrategy._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class ConcurrentAtomicNumberSuite[A, R <: AtomicNumber[A]](
  builder: AtomicBuilder[A, R],
  strategy: PaddingStrategy,
  allowPlatformIntrinsics: Boolean,
)(implicit ev: Numeric[A])
  extends SimpleTestSuite {

  def Atomic(initial: A): R = builder.buildInstance(initial, strategy, allowPlatformIntrinsics)
  val two = ev.plus(ev.one, ev.one)

  test("should perform concurrent compareAndSet") {
    val r = Atomic(ev.zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (_ <- 0 until 100)
          r.increment()
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == ev.fromInt(500))
  }

  test("should perform concurrent getAndSet") {
    val r = Atomic(ev.zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (j <- 0 until 100)
          r.getAndSet(ev.fromInt(j + 1))
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == ev.fromInt(100))
  }

  test("should perform concurrent increment") {
    val r = Atomic(ev.zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (_ <- 0 until 100)
          r.increment()
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == ev.fromInt(500))
  }

  test("should perform concurrent incrementAndGet") {
    val r = Atomic(ev.zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (_ <- 0 until 100)
          r.incrementAndGet()
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == ev.fromInt(500))
  }

  test("should perform concurrent getAndIncrement") {
    val r = Atomic(ev.zero)
    val futures =
      for (_ <- 0 until 5) yield Future {
        for (_ <- 0 until 100)
          r.getAndIncrement()
      }

    val f = Future.sequence(futures)
    Await.result(f, 30.seconds)
    assert(r.get() == ev.fromInt(500))
  }
}

//-- NoPadding (Java 8)

object ConcurrentAtomicNumberDoubleNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    NoPadding,
    allowPlatformIntrinsics = true
  )

//--Left64 (Java 8)

object ConcurrentAtomicNumberDoubleLeft64Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatLeft64Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongLeft64Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntLeft64Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortLeft64Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteLeft64Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharLeft64Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyLeft64Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left64,
    allowPlatformIntrinsics = true
  )

//-- Right64 (Java 8)

object ConcurrentAtomicNumberDoubleRight64Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatRight64Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongRight64Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntRight64Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortRight64Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteRight64Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharRight64Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyRight64Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right64,
    allowPlatformIntrinsics = true
  )

//-- LeftRight128 (Java 8)

object ConcurrentAtomicNumberDoubleLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

//--Left128 (Java 8)

object ConcurrentAtomicNumberDoubleLeft128Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatLeft128Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongLeft128Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntLeft128Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortLeft128Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteLeft128Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharLeft128Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyLeft128Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left128,
    allowPlatformIntrinsics = true
  )

//-- Right128 (Java 8)

object ConcurrentAtomicNumberDoubleRight128Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatRight128Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongRight128Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntRight128Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortRight128Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteRight128Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharRight128Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyRight128Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right128,
    allowPlatformIntrinsics = true
  )

//-- LeftRight256 (Java 8)

object ConcurrentAtomicNumberDoubleLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberFloatLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberLongLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberIntLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberShortLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberByteLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberCharLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

object ConcurrentAtomicNumberNumberAnyLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

// ------------ Java 7

//-- NoPadding (Java 7)

object ConcurrentAtomicNumberDoubleNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    NoPadding,
    allowPlatformIntrinsics = false
  )

//--Left64 (Java 7)

object ConcurrentAtomicNumberDoubleLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left64,
    allowPlatformIntrinsics = false
  )

//-- Right64 (Java 7)

object ConcurrentAtomicNumberDoubleRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right64,
    allowPlatformIntrinsics = false
  )

//-- LeftRight128 (Java 7)

object ConcurrentAtomicNumberDoubleLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

//--Left128 (Java 7)

object ConcurrentAtomicNumberDoubleLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left128,
    allowPlatformIntrinsics = false
  )

//-- Right128 (Java 7)

object ConcurrentAtomicNumberDoubleRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right128,
    allowPlatformIntrinsics = false
  )

//-- LeftRight256 (Java 7)

object ConcurrentAtomicNumberDoubleLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberFloatLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberLongLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberIntLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberShortLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberByteLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberCharLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

object ConcurrentAtomicNumberNumberAnyLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight256,
    allowPlatformIntrinsics = false
  )
