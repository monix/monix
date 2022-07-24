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

abstract class ConcurrentAtomicNumberSuite[A, R <: AtomicNumber[A]](
  builder: AtomicBuilder[A, R],
  strategy: PaddingStrategy,
  allowPlatformIntrinsics: Boolean
)(
  implicit ev: Numeric[A]
) extends FunSuite {

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

class ConcurrentAtomicNumberDoubleNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyNoPaddingSuite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    NoPadding,
    allowPlatformIntrinsics = true
  )

//--Left64 (Java 8)

class ConcurrentAtomicNumberDoubleLeft64Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatLeft64Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongLeft64Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntLeft64Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortLeft64Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteLeft64Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharLeft64Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyLeft64Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left64,
    allowPlatformIntrinsics = true
  )

//-- Right64 (Java 8)

class ConcurrentAtomicNumberDoubleRight64Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatRight64Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongRight64Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntRight64Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortRight64Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteRight64Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharRight64Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyRight64Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right64,
    allowPlatformIntrinsics = true
  )

//-- LeftRight128 (Java 8)

class ConcurrentAtomicNumberDoubleLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyLeftRight128Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight128,
    allowPlatformIntrinsics = true
  )

//--Left128 (Java 8)

class ConcurrentAtomicNumberDoubleLeft128Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatLeft128Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongLeft128Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntLeft128Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortLeft128Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteLeft128Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharLeft128Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyLeft128Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left128,
    allowPlatformIntrinsics = true
  )

//-- Right128 (Java 8)

class ConcurrentAtomicNumberDoubleRight128Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatRight128Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongRight128Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntRight128Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortRight128Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteRight128Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharRight128Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyRight128Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right128,
    allowPlatformIntrinsics = true
  )

//-- LeftRight256 (Java 8)

class ConcurrentAtomicNumberDoubleLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberFloatLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberLongLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberIntLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberShortLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberByteLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberCharLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

class ConcurrentAtomicNumberNumberAnyLeftRight256Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight256,
    allowPlatformIntrinsics = true
  )

// ------------ Java 7

//-- NoPadding (Java 7)

class ConcurrentAtomicNumberDoubleNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    NoPadding,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyNoPaddingJava7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    NoPadding,
    allowPlatformIntrinsics = false
  )

//--Left64 (Java 7)

class ConcurrentAtomicNumberDoubleLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyLeft64Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left64,
    allowPlatformIntrinsics = false
  )

//-- Right64 (Java 7)

class ConcurrentAtomicNumberDoubleRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right64,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyRight64Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right64,
    allowPlatformIntrinsics = false
  )

//-- LeftRight128 (Java 7)

class ConcurrentAtomicNumberDoubleLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyLeftRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight128,
    allowPlatformIntrinsics = false
  )

//--Left128 (Java 7)

class ConcurrentAtomicNumberDoubleLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Left128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyLeft128Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Left128,
    allowPlatformIntrinsics = false
  )

//-- Right128 (Java 7)

class ConcurrentAtomicNumberDoubleRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    Right128,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyRight128Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    Right128,
    allowPlatformIntrinsics = false
  )

//-- LeftRight256 (Java 7)

class ConcurrentAtomicNumberDoubleLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
    Atomic.builderFor(0.0),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberFloatLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
    Atomic.builderFor(0.0f),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberLongLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
    Atomic.builderFor(0L),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberIntLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
    Atomic.builderFor(0),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberShortLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
    Atomic.builderFor(0.toShort),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberByteLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
    Atomic.builderFor(0.toByte),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberCharLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
    Atomic.builderFor(0.toChar),
    LeftRight256,
    allowPlatformIntrinsics = false
  )

class ConcurrentAtomicNumberNumberAnyLeftRight256Java7Suite
  extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
    Atomic.builderFor(BigInt(0)),
    LeftRight256,
    allowPlatformIntrinsics = false
  )
