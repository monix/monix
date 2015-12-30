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
import scala.concurrent.{Await, Future}
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{TimeUnit, CountDownLatch}

abstract class ConcurrentAtomicNumberSuite[T, R <: AtomicNumber[T]]
  (name: String, builder: AtomicBuilder[T, R],
   value: T, nan1: Option[T], maxValue: T, minValue: T)(implicit ev: Numeric[T])
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial)
  val two = ev.plus(ev.one, ev.one)

  test("should perform concurrent compareAndSet") {
    val r = Atomic(ev.zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.increment()
    }

    val f = Future.sequence(futures)
    Await.result(f, 1.second)
    assert(r.get == ev.fromInt(500))
  }

  test("should perform concurrent getAndSet") {
    val r = Atomic(ev.zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.getAndSet(ev.fromInt(j + 1))
    }

    val f = Future.sequence(futures)
    Await.result(f, 1.second)
    assert(r.get == ev.fromInt(100))
  }
}

object ConcurrentAtomicNumberDoubleSuite extends ConcurrentAtomicNumberSuite[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.0), 17.23, Some(Double.NaN), Double.MaxValue, Double.MinValue)

object ConcurrentAtomicNumberFloatSuite extends ConcurrentAtomicNumberSuite[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), 17.23f, Some(Float.NaN), Float.MaxValue, Float.MinValue)

object ConcurrentAtomicNumberLongSuite extends ConcurrentAtomicNumberSuite[Long, AtomicLong](
  "AtomicLong", Atomic.builderFor(0L), -782L, None, Long.MaxValue, Long.MinValue)

object ConcurrentAtomicNumberIntSuite extends ConcurrentAtomicNumberSuite[Int, AtomicInt](
  "AtomicInt", Atomic.builderFor(0), 782, None, Int.MaxValue, Int.MinValue)

object ConcurrentAtomicNumberShortSuite extends ConcurrentAtomicNumberSuite[Short, AtomicShort](
  "AtomicShort", Atomic.builderFor(0.toShort), 782.toShort, None, Short.MaxValue, Short.MinValue)

object ConcurrentAtomicNumberByteSuite extends ConcurrentAtomicNumberSuite[Byte, AtomicByte](
  "AtomicByte", Atomic.builderFor(0.toByte), 782.toByte, None, Byte.MaxValue, Byte.MinValue)

object ConcurrentAtomicNumberCharSuite extends ConcurrentAtomicNumberSuite[Char, AtomicChar](
  "AtomicChar", Atomic.builderFor(0.toChar), 782.toChar, None, Char.MaxValue, Char.MinValue)

object ConcurrentAtomicNumberNumberAnySuite extends ConcurrentAtomicNumberSuite[BigInt, AtomicNumberAny[BigInt]](
  "AtomicNumberAny", Atomic.builderFor(BigInt(0)), BigInt(Int.MaxValue), None, BigInt(Long.MaxValue), BigInt(Long.MinValue))
