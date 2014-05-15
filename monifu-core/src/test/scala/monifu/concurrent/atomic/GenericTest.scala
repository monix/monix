package monifu.concurrent.atomic

import org.scalatest.FunSpec
import scala.concurrent.{TimeoutException, Await, Future}
import java.util.concurrent.{TimeUnit, CountDownLatch}
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


abstract class GenericTest[T, R <: Atomic[T] with BlockableAtomic[T]]
  (name: String, builder: AtomicBuilder[T, R], valueFromInt: Int => T, valueToInt: T => Int)
  extends FunSpec {

  def Atomic(initial: T): R = builder.buildInstance(initial)

  def zero = valueFromInt(0)
  def one = valueFromInt(1)
  def two = valueFromInt(2)

  it("should set()") {
    val r = Atomic(zero)
    assert(r.get === zero)
    r.set(one)
    assert(r.get === one)
  }

  it("should getAndSet()") {
    val r = Atomic(zero)
    assert(r.get === zero)
    val old = r.getAndSet(one)
    assert(old === zero)
    assert(r.get === one)
  }
  
  it("should compareAndSet()") {
    val r = Atomic(zero)
    assert(r.get === zero)

    assert(r.compareAndSet(zero, one))
    assert(r.get === one)
    assert(r.compareAndSet(one, zero))
    assert(r.get === zero)
    assert(!r.compareAndSet(one, one))
    assert(r.get === zero)
  }

  it("should transform") {
    val r = Atomic(zero)
    assert(r.get === zero)

    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assert(r.get === one)
    r.transform(x => valueFromInt(valueToInt(x) + 1))
    assert(r.get === two)
  }

  it("should transformAndGet") {
    val r = Atomic(zero)
    assert(r.get === zero)

    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) === one)
    assert(r.transformAndGet(x => valueFromInt(valueToInt(x) + 1)) === two)
    assert(r.get === two)
  }

  it("should getAndTransform") {
    val r = Atomic(zero)
    assert(r.get === zero)

    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) === zero)
    assert(r.getAndTransform(x => valueFromInt(valueToInt(x) + 1)) === one)
    assert(r.get === two)
  }

  it("should transformAndExtract") {
    val r = Atomic(zero)
    assert(r.get === zero)

    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) === zero)
    assert(r.transformAndExtract(x => (x, valueFromInt(valueToInt(x) + 1))) === one)
    assert(r.get === two)
  }

  it("should perform concurrent compareAndSet") {
    val r = Atomic(zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.transform(x => valueFromInt(valueToInt(x) + 1))
    }

    val f = Future.sequence(futures)
    Await.result(f, 1.second)
    assert(r.get === valueFromInt(500))
  }

  it("should perform concurrent getAndSet") {
    val r = Atomic(zero)
    val futures = for (i <- 0 until 5) yield Future {
      for (j <- 0 until 100)
        r.getAndSet(valueFromInt(j))
    }

    val f = Future.sequence(futures)
    Await.result(f, 1.second)
    assert(r.get === valueFromInt(99))
  }

  it("should waitForCompareAndSet") {
    val r = Atomic(one)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    Future {
      start.countDown()
      r.waitForCompareAndSet(zero, one)
      done.countDown()
    }

    start.await(1, TimeUnit.SECONDS)
    assert(done.getCount === 1 && r.get === one)
    r.set(zero)
    done.await(1, TimeUnit.SECONDS)
    assert(r.get === one)
  }

  it("should fail on waitForCompareAndSet with maxRetries") {
    val a = Atomic(one)

    val r = a.waitForCompareAndSet(zero, one, maxRetries = 1000)
    assert(r === false)
  }

  it("should succeed on waitForCompareAndSet with maxRetries") {
    val a = Atomic(zero)

    val r = a.waitForCompareAndSet(zero, one, maxRetries = 1000)
    assert(r === true)
  }

  it("should fail on waitForCompareAndSet with duration") {
    val a = Atomic(one)

    val startAt = System.nanoTime()
    intercept[TimeoutException] {
      a.waitForCompareAndSet(zero, one, waitAtMost = 100.millis)
    }

    val duration = (System.nanoTime() - startAt).nanos
    assert(duration >= 100.millis)
  }

  it("should succeed on waitForCompareAndSet with duration") {
    val a = Atomic(zero)
    a.waitForCompareAndSet(zero, one, waitAtMost = 100.millis)
  }

  it("should waitForValue") {
    val a = Atomic(zero)

    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    Future {
      start.countDown()
      a.waitForValue(one)
      done.countDown()
    }

    start.await(1, TimeUnit.SECONDS)
    assert(done.getCount === 1)
    a.set(one)
    done.await(1, TimeUnit.SECONDS)
  }

  it("should fail on waitForValue with duration") {
    val a = Atomic(zero)

    val startAt = System.nanoTime()
    intercept[TimeoutException] {
      a.waitForValue(one, 100.millis)
    }

    val duration = (System.nanoTime() - startAt).nanos
    assert(duration >= 100.millis)
  }

  it("should succeed on waitForValue with duration") {
    val a = Atomic(zero)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)

    Future {
      start.countDown()
      a.waitForValue(one, 100.millis)
      done.countDown()
    }

    start.await(1, TimeUnit.SECONDS)
    assert(done.getCount === 1)
    a.set(one)
    done.await(1, TimeUnit.SECONDS)
  }

  it("should waitForCondition") {
    val a = Atomic(zero)

    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    Future {
      start.countDown()
      a.waitForCondition(_ == one)
      done.countDown()
    }

    start.await(1, TimeUnit.SECONDS)
    assert(done.getCount === 1)
    a.set(one)
    done.await(1, TimeUnit.SECONDS)
  }

  it("should fail on waitForCondition with duration") {
    val a = Atomic(zero)

    val startAt = System.nanoTime()
    intercept[TimeoutException] {
      a.waitForCondition(100.millis, { x: T => x == one })
    }

    val duration = (System.nanoTime() - startAt).nanos
    assert(duration >= 100.millis)
  }

  it("should succeed on waitForCondition with duration") {
    val a = Atomic(zero)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(1)

    Future {
      start.countDown()
      a.waitForCondition(100.millis, { x: T => x == one })
      done.countDown()
    }

    start.await(1, TimeUnit.SECONDS)
    assert(done.getCount === 1)
    a.set(one)
    done.await(1, TimeUnit.SECONDS)
  }
}

class GenericAtomicAnyTest extends GenericTest[String, AtomicAny[String]](
  "AtomicAny", Atomic.builderFor(""), x => x.toString, x => x.toInt)

class GenericAtomicBooleanTest extends GenericTest[Boolean, AtomicBoolean](
  "AtomicBoolean", Atomic.builderFor(true), x => if (x == 1) true else false, x => if (x) 1 else 0)

class GenericAtomicNumberAnyTest extends GenericTest[BigInt, AtomicNumberAny[BigInt]](
  "AtomicNumberAny", Atomic.builderFor(BigInt(0)), x => BigInt(x), x => x.toInt)

class GenericAtomicFloatTest extends GenericTest[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), x => x.toFloat, x => x.toInt)

class GenericAtomicDoubleTest extends GenericTest[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.toDouble), x => x.toDouble, x => x.toInt)

class GenericAtomicShortTest extends GenericTest[Short, AtomicShort](
  "AtomicShort", Atomic.builderFor(0.toShort), x => x.toShort, x => x.toInt)

class GenericAtomicByteTest extends GenericTest[Byte, AtomicByte](
  "AtomicByte", Atomic.builderFor(0.toByte), x => x.toByte, x => x.toInt)

class GenericAtomicCharTest extends GenericTest[Char, AtomicChar](
  "AtomicChar", Atomic.builderFor(0.toChar), x => x.toChar, x => x.toInt)

class GenericAtomicIntTest extends GenericTest[Int, AtomicInt](
  "AtomicInt", Atomic.builderFor(0), x => x, x => x)

class GenericAtomicLongTest extends GenericTest[Long, AtomicLong](
  "AtomicLong", Atomic.builderFor(0.toLong), x => x.toLong, x => x.toInt)

