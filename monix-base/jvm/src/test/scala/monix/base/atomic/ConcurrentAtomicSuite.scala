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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import minitest.SimpleTestSuite
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, TimeoutException}


abstract class ConcurrentAtomicSuite[T, R <: Atomic[T] with BlockableAtomic[T]]
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

  test("should waitForCompareAndSet") {
    val r = Atomic(one)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    Future {
      start.countDown()
      r.waitForCompareAndSet(zero, one)
      done.countDown()
    }

    start.await(5, TimeUnit.SECONDS)
    assert(done.getCount == 1 && r.get == one)
    r.set(zero)
    done.await(5, TimeUnit.SECONDS)
    assert(r.get == one)
  }

  test("should fail on waitForCompareAndSet with maxRetries") {
    val a = Atomic(one)

    val r = a.waitForCompareAndSet(zero, one, maxRetries = 1000)
    assert(!r)
  }

  test("should succeed on waitForCompareAndSet with maxRetries") {
    val a = Atomic(zero)

    val r = a.waitForCompareAndSet(zero, one, maxRetries = 1000)
    assert(r)
  }

  test("should fail on waitForCompareAndSet with duration") {
    val a = Atomic(one)

    val startAt = System.nanoTime()
    intercept[TimeoutException] {
      a.waitForCompareAndSet(zero, one, waitAtMost = 100.millis)
    }

    val duration = (System.nanoTime() - startAt).nanos
    assert(duration >= 100.millis)
  }

  test("should succeed on waitForCompareAndSet with duration") {
    val a = Atomic(zero)
    a.waitForCompareAndSet(zero, one, waitAtMost = 100.millis)
  }

  test("should waitForValue") {
    val a = Atomic(zero)

    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    Future {
      start.countDown()
      a.waitForValue(one)
      done.countDown()
    }

    start.await(5, TimeUnit.SECONDS)
    assert(done.getCount == 1)
    a.set(one)
    done.await(5, TimeUnit.SECONDS)
  }

  test("should fail on waitForValue with duration") {
    val a = Atomic(zero)

    val startAt = System.nanoTime()
    intercept[TimeoutException] {
      a.waitForValue(one, 100.millis)
    }

    val duration = (System.nanoTime() - startAt).nanos
    assert(duration >= 100.millis)
  }

  test("should succeed on waitForValue with duration") {
    val a = Atomic(zero)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)

    Future {
      start.countDown()
      a.waitForValue(one, 100.millis)
      done.countDown()
    }

    start.await(5, TimeUnit.SECONDS)
    assert(done.getCount == 1)
    a.set(one)
    done.await(5, TimeUnit.SECONDS)
  }

  test("should waitForCondition") {
    val a = Atomic(zero)

    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    Future {
      start.countDown()
      a.waitForCondition(_ == one)
      done.countDown()
    }

    start.await(5, TimeUnit.SECONDS)
    assert(done.getCount == 1)
    a.set(one)
    done.await(5, TimeUnit.SECONDS)
  }

  test("should fail on waitForCondition with duration") {
    val a = Atomic(zero)

    val startAt = System.nanoTime()
    intercept[TimeoutException] {
      a.waitForCondition(100.millis, { x: T => x == one })
    }

    val duration = (System.nanoTime() - startAt).nanos
    assert(duration >= 100.millis)
  }

  test("should succeed on waitForCondition with duration") {
    val a = Atomic(zero)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)

    Future {
      start.countDown()
      a.waitForCondition(100.millis, { x: T => x == one })
      done.countDown()
    }

    start.await(5, TimeUnit.SECONDS)
    assert(done.getCount == 1)
    a.set(one)
    done.await(5, TimeUnit.SECONDS)
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
