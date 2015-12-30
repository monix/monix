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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import minitest.SimpleTestSuite
import monix.concurrent.Implicits.globalScheduler
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}


abstract class ConcurrentAtomicSuite[T, R <: Atomic[T]]
  (name: String, builder: AtomicBuilder[T, R], valueFromInt: Int => T, valueToInt: T => Int)
  extends SimpleTestSuite {

  def Atomic(initial: T): R = builder.buildInstance(initial)

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
    Await.result(f, 5.second)
    assert(r.get == valueFromInt(500))
  }

  test("should perform concurrent getAndSet") {
    val r = Atomic(zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.getAndSet(valueFromInt(j))
    }

    val f = Future.sequence(futures)
    Await.result(f, 5.second)
    assert(r.get == valueFromInt(99))
  }
}

object ConcurrentAtomicAnySuite extends ConcurrentAtomicSuite[String, AtomicAny[String]](
  "AtomicAny", Atomic.builderFor(""), x => x.toString, x => x.toInt)

object ConcurrentAtomicBooleanSuite extends ConcurrentAtomicSuite[Boolean, AtomicBoolean](
  "AtomicBoolean", Atomic.builderFor(true), x => if (x == 1) true else false, x => if (x) 1 else 0)

object ConcurrentAtomicNumberAnySuite extends ConcurrentAtomicSuite[BigInt, AtomicNumberAny[BigInt]](
  "AtomicNumberAny", Atomic.builderFor(BigInt(0)), x => BigInt(x), x => x.toInt)

object ConcurrentAtomicFloatSuite extends ConcurrentAtomicSuite[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), x => x.toFloat, x => x.toInt)

object ConcurrentAtomicDoubleSuite extends ConcurrentAtomicSuite[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.toDouble), x => x.toDouble, x => x.toInt)

object ConcurrentAtomicShortSuite extends ConcurrentAtomicSuite[Short, AtomicShort](
  "AtomicShort", Atomic.builderFor(0.toShort), x => x.toShort, x => x.toInt)

object ConcurrentAtomicByteSuite extends ConcurrentAtomicSuite[Byte, AtomicByte](
  "AtomicByte", Atomic.builderFor(0.toByte), x => x.toByte, x => x.toInt)

object ConcurrentAtomicCharSuite extends ConcurrentAtomicSuite[Char, AtomicChar](
  "AtomicChar", Atomic.builderFor(0.toChar), x => x.toChar, x => x.toInt)

object ConcurrentAtomicIntSuite extends ConcurrentAtomicSuite[Int, AtomicInt](
  "AtomicInt", Atomic.builderFor(0), x => x, x => x)

object ConcurrentAtomicLongSuite extends ConcurrentAtomicSuite[Long, AtomicLong](
  "AtomicLong", Atomic.builderFor(0.toLong), x => x.toLong, x => x.toInt)

