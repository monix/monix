package monifu.concurrent.atomic

import org.scalatest.FunSpec
import scala.concurrent.{Await, Future}
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{TimeUnit, CountDownLatch}

abstract class AtomicNumberTest[T, R <: AtomicNumber[T] with BlockableAtomic[T]]
  (name: String, builder: AtomicBuilder[T, R],
   value: T, nan1: Option[T], maxValue: T, minValue: T)(implicit ev: Numeric[T])
  extends FunSpec {

  def Atomic(initial: T): R = builder.buildInstance(initial)

  val two = ev.plus(ev.one, ev.one)

  describe(name) {
    it("should get()") {
      assert(Atomic(value).get === value)
      assert(Atomic(maxValue).get === maxValue)
      assert(Atomic(minValue).get === minValue)
    }

    it("should set()") {
      val r = Atomic(ev.zero)
      r.set(value)
      assert(r.get === value)
      r.set(minValue)
      assert(r.get === minValue)
      r.set(maxValue)
      assert(r.get === maxValue)
    }

    it("should compareAndSet()") {
      val r = Atomic(ev.zero)
      assert(r.compareAndSet(ev.zero, ev.one) === true)
      assert(r.compareAndSet(ev.zero, ev.one) === false)

      for (n1 <- nan1) {
        assert(r.compareAndSet(ev.one, n1) === true)
        assert(r.compareAndSet(n1, ev.one) === true)
      }

      assert(r.get === ev.one)
    }

    it("should getAndSet()") {
      val r = Atomic(ev.zero)
      assert(r.getAndSet(ev.one) === ev.zero)
      assert(r.getAndSet(value) === ev.one)
      assert(r.getAndSet(minValue) === value)
      assert(r.getAndSet(maxValue) === minValue)
      assert(r.get === maxValue)
    }

    it("should increment()") {
      val r = Atomic(value)
      r.increment()
      assert(r.get === ev.plus(value, ev.one))
      r.increment()
      assert(r.get === ev.plus(value, ev.plus(ev.one, ev.one)))
    }

    it("should increment(value)") {
      val r = Atomic(value)
      r.increment(ev.toInt(value))
      assert(r.get === ev.plus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should decrement()") {
      val r = Atomic(value)
      r.decrement()
      assert(r.get === ev.minus(value, ev.one))
      r.decrement()
      assert(r.get === ev.minus(value, ev.plus(ev.one, ev.one)))
    }

    it("should decrement(value)") {
      val r = Atomic(value)
      r.decrement(ev.toInt(value))
      assert(r.get === ev.minus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should incrementAndGet()") {
      val r = Atomic(value)
      assert(r.incrementAndGet() === ev.plus(value, ev.one))
      assert(r.incrementAndGet() === ev.plus(value, ev.plus(ev.one, ev.one)))
    }

    it("should incrementAndGet(value)") {
      val r = Atomic(value)
      assert(r.incrementAndGet(ev.toInt(value)) === ev.plus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should decrementAndGet()") {
      val r = Atomic(value)
      assert(r.decrementAndGet() === ev.minus(value, ev.one))
      assert(r.decrementAndGet() === ev.minus(value, ev.plus(ev.one, ev.one)))
    }

    it("should decrementAndGet(value)") {
      val r = Atomic(value)
      assert(r.decrementAndGet(ev.toInt(value)) === ev.minus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should getAndIncrement()") {
      val r = Atomic(value)
      assert(r.getAndIncrement() === value)
      assert(r.getAndIncrement() === ev.plus(value, ev.one))
      assert(r.get === ev.plus(value, two))
    }

    it("should getAndIncrement(value)") {
      val r = Atomic(value)
      assert(r.getAndIncrement(2) === value)
      assert(r.getAndIncrement(2) === ev.plus(value, two))
      assert(r.get === ev.plus(value, ev.plus(two, two)))
    }

    it("should getAndDecrement()") {
      val r = Atomic(value)
      assert(r.getAndDecrement() === value)
      assert(r.getAndDecrement() === ev.minus(value, ev.one))
      assert(r.get === ev.minus(value, two))
    }

    it("should getAndDecrement(value)") {
      val r = Atomic(value)
      assert(r.getAndDecrement(2) === value)
      assert(r.getAndDecrement(2) === ev.minus(value, two))
      assert(r.get === ev.minus(value, ev.plus(two, two)))
    }

    it("should addAndGet(value)") {
      val r = Atomic(value)
      assert(r.addAndGet(value) === ev.plus(value, value))
      assert(r.addAndGet(value) === ev.plus(value, ev.plus(value, value)))
    }

    it("should getAndAdd(value)") {
      val r = Atomic(value)
      assert(r.getAndAdd(value) === value)
      assert(r.get === ev.plus(value, value))
    }

    it("should subtractAndGet(value)") {
      val r = Atomic(value)
      assert(r.subtractAndGet(value) === ev.minus(value, value))
      assert(r.subtractAndGet(value) === ev.minus(value, ev.plus(value, value)))
    }

    it("should getAndSubtract(value)") {
      val r = Atomic(value)
      assert(r.getAndSubtract(value) === value)
      assert(r.get === ev.zero)
    }

    it("should transform()") {
      val r = Atomic(value)
      r.transform(x => ev.plus(x, x))
      assert(r.get === ev.plus(value, value))
    }

    it("should transformAndGet()") {
      val r = Atomic(value)
      assert(r.transformAndGet(x => ev.plus(x, x)) === ev.plus(value, value))
    }

    it("should getAndTransform()") {
      val r = Atomic(value)
      assert(r.getAndTransform(x => ev.plus(x, x)) === value)
      assert(r.get === ev.plus(value, value))
    }

    it("should maybe overflow on max") {
      val r = Atomic(maxValue)
      r.increment()
      assert(r.get === ev.plus(maxValue, ev.one))
    }

    it("should maybe overflow on min") {
      val r = Atomic(minValue)
      r.decrement()
      assert(r.get === ev.minus(minValue, ev.one))
    }

    it("should perform concurrent compareAndSet") {
      val r = Atomic(ev.zero)
      val futures = for (i <- 0 until 5) yield Future {
        for (j <- 0 until 100)
          r.increment()
      }

      val f = Future.sequence(futures)
      Await.result(f, 1.second)
      assert(r.get === ev.fromInt(500))
    }

    it("should perform concurrent getAndSet") {
      val r = Atomic(ev.zero)
      val futures = for (i <- 0 until 5) yield Future {
        for (j <- 0 until 100)
          r.getAndSet(ev.fromInt(j + 1))
      }

      val f = Future.sequence(futures)
      Await.result(f, 1.second)
      assert(r.get === ev.fromInt(100))
    }

    it("should waitForCompareAndSet") {
      val r = Atomic(ev.one)
      val start = new CountDownLatch(1)
      val done = new CountDownLatch(1)
      Future { start.countDown(); r.waitForCompareAndSet(ev.zero, ev.one); done.countDown() }

      start.await(1, TimeUnit.SECONDS)
      assert(done.getCount === 1)
      r.set(ev.zero)
      done.await(1, TimeUnit.SECONDS)
    }
  }
}

class AtomicDoubleTest extends AtomicNumberTest[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.0), 17.23, Some(Double.NaN), Double.MaxValue, Double.MinValue) {

  describe("AtomicDouble") {
    it("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
      assert(Atomic(Double.MinPositiveValue).get === Double.MinPositiveValue)
      assert(Atomic(Double.NaN).get.isNaN === true)
      assert(Atomic(Double.NegativeInfinity).get.isNegInfinity === true)
      assert(Atomic(Double.PositiveInfinity).get.isPosInfinity === true)
    }
  }
}

class AtomicFloatTest extends AtomicNumberTest[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), 17.23f, Some(Float.NaN), Float.MaxValue, Float.MinValue) {

  describe("AtomicFloat") {
    it("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
      assert(Atomic(Float.MinPositiveValue).get === Float.MinPositiveValue)
      assert(Atomic(Float.NaN).get.isNaN === true)
      assert(Atomic(Float.NegativeInfinity).get.isNegInfinity === true)
      assert(Atomic(Float.PositiveInfinity).get.isPosInfinity === true)
    }
  }
}

class AtomicLongTest extends AtomicNumberTest[Long, AtomicLong](
  "AtomicLong", Atomic.builderFor(0L), -782L, None, Long.MaxValue, Long.MinValue)

class AtomicIntTest extends AtomicNumberTest[Int, AtomicInt](
  "AtomicInt", Atomic.builderFor(0), 782, None, Int.MaxValue, Int.MinValue)

class AtomicShortTest extends AtomicNumberTest[Short, AtomicShort](
  "AtomicShort", Atomic.builderFor(0.toShort), 782.toShort, None, Short.MaxValue, Short.MinValue)

class AtomicByteTest extends AtomicNumberTest[Byte, AtomicByte](
  "AtomicByte", Atomic.builderFor(0.toByte), 782.toByte, None, Byte.MaxValue, Byte.MinValue)

class AtomicCharTest extends AtomicNumberTest[Char, AtomicChar](
  "AtomicChar", Atomic.builderFor(0.toChar), 782.toChar, None, Char.MaxValue, Char.MinValue)

class AtomicNumberAnyTest extends AtomicNumberTest[BigInt, AtomicNumberAny[BigInt]](
  "AtomicNumberAny", Atomic.builderFor(BigInt(0)), BigInt(Int.MaxValue).toChar, None, BigInt(Long.MaxValue), BigInt(Long.MinValue))
