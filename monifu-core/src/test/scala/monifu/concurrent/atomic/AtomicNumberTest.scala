package monifu.concurrent.atomic

import monifu.test.MonifuTest

abstract class AtomicNumberTest[T, R <: AtomicNumber[T]]
  (name: String, builder: AtomicBuilder[T, R],
   value: T, nan1: Option[T], maxValue: T, minValue: T)(implicit ev: Numeric[T])
  extends MonifuTest {

  def Atomic(initial: T): R = builder.buildInstance(initial)

  val two = ev.plus(ev.one, ev.one)

  describe(name) {
    it("should get()") {
      expect(Atomic(value).get).toBe(value)
      expect(Atomic(maxValue).get).toBe(maxValue)
      expect(Atomic(minValue).get).toBe(minValue)
    }

    it("should set()") {
      val r = Atomic(ev.zero)
      r.set(value)
      expect(r.get).toBe(value)
      r.set(minValue)
      expect(r.get).toBe(minValue)
      r.set(maxValue)
      expect(r.get).toBe(maxValue)
    }

    it("should compareAndSet()") {
      val r = Atomic(ev.zero)
      expect(r.compareAndSet(ev.zero, ev.one)).toBe(true)
      expect(r.compareAndSet(ev.zero, ev.one)).toBe(false)

      for (n1 <- nan1) {
        expect(r.compareAndSet(ev.one, n1)).toBe(true)
        expect(r.compareAndSet(n1, ev.one)).toBe(true)
      }

      expect(r.get).toBe(ev.one)
    }

    it("should getAndSet()") {
      val r = Atomic(ev.zero)
      expect(r.getAndSet(ev.one)).toBe(ev.zero)
      expect(r.getAndSet(value)).toBe(ev.one)
      expect(r.getAndSet(minValue)).toBe(value)
      expect(r.getAndSet(maxValue)).toBe(minValue)
      expect(r.get).toBe(maxValue)
    }

    it("should increment()") {
      val r = Atomic(value)
      r.increment()
      expect(r.get).toBe(ev.plus(value, ev.one))
      r.increment()
      expect(r.get).toBe(ev.plus(value, ev.plus(ev.one, ev.one)))
    }

    it("should increment(value)") {
      val r = Atomic(value)
      r.increment(ev.toInt(value))
      expect(r.get).toBe(ev.plus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should decrement()") {
      val r = Atomic(value)
      r.decrement()
      expect(r.get).toBe(ev.minus(value, ev.one))
      r.decrement()
      expect(r.get).toBe(ev.minus(value, ev.plus(ev.one, ev.one)))
    }

    it("should decrement(value)") {
      val r = Atomic(value)
      r.decrement(ev.toInt(value))
      expect(r.get).toBe(ev.minus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should incrementAndGet()") {
      val r = Atomic(value)
      expect(r.incrementAndGet()).toBe(ev.plus(value, ev.one))
      expect(r.incrementAndGet()).toBe(ev.plus(value, ev.plus(ev.one, ev.one)))
    }

    it("should incrementAndGet(value)") {
      val r = Atomic(value)
      expect(r.incrementAndGet(ev.toInt(value))).toBe(ev.plus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should decrementAndGet()") {
      val r = Atomic(value)
      expect(r.decrementAndGet()).toBe(ev.minus(value, ev.one))
      expect(r.decrementAndGet()).toBe(ev.minus(value, ev.plus(ev.one, ev.one)))
    }

    it("should decrementAndGet(value)") {
      val r = Atomic(value)
      expect(r.decrementAndGet(ev.toInt(value))).toBe(ev.minus(value, ev.fromInt(ev.toInt(value))))
    }

    it("should getAndIncrement()") {
      val r = Atomic(value)
      expect(r.getAndIncrement()).toBe(value)
      expect(r.getAndIncrement()).toBe(ev.plus(value, ev.one))
      expect(r.get).toBe(ev.plus(value, two))
    }

    it("should getAndIncrement(value)") {
      val r = Atomic(value)
      expect(r.getAndIncrement(2)).toBe(value)
      expect(r.getAndIncrement(2)).toBe(ev.plus(value, two))
      expect(r.get).toBe(ev.plus(value, ev.plus(two, two)))
    }

    it("should getAndDecrement()") {
      val r = Atomic(value)
      expect(r.getAndDecrement()).toBe(value)
      expect(r.getAndDecrement()).toBe(ev.minus(value, ev.one))
      expect(r.get).toBe(ev.minus(value, two))
    }

    it("should getAndDecrement(value)") {
      val r = Atomic(value)
      expect(r.getAndDecrement(2)).toBe(value)
      expect(r.getAndDecrement(2)).toBe(ev.minus(value, two))
      expect(r.get).toBe(ev.minus(value, ev.plus(two, two)))
    }

    it("should addAndGet(value)") {
      val r = Atomic(value)
      expect(r.addAndGet(value)).toBe(ev.plus(value, value))
      expect(r.addAndGet(value)).toBe(ev.plus(value, ev.plus(value, value)))
    }

    it("should getAndAdd(value)") {
      val r = Atomic(value)
      expect(r.getAndAdd(value)).toBe(value)
      expect(r.get).toBe(ev.plus(value, value))
    }

    it("should subtractAndGet(value)") {
      val r = Atomic(value)
      expect(r.subtractAndGet(value)).toBe(ev.minus(value, value))
      expect(r.subtractAndGet(value)).toBe(ev.minus(value, ev.plus(value, value)))
    }

    it("should getAndSubtract(value)") {
      val r = Atomic(value)
      expect(r.getAndSubtract(value)).toBe(value)
      expect(r.get).toBe(ev.zero)
    }

    it("should transform()") {
      val r = Atomic(value)
      r.transform(x => ev.plus(x, x))
      expect(r.get).toBe(ev.plus(value, value))
    }

    it("should transformAndGet()") {
      val r = Atomic(value)
      expect(r.transformAndGet(x => ev.plus(x, x))).toBe(ev.plus(value, value))
    }

    it("should getAndTransform()") {
      val r = Atomic(value)
      expect(r.getAndTransform(x => ev.plus(x, x))).toBe(value)
      expect(r.get).toBe(ev.plus(value, value))
    }

    it("should maybe overflow on max") {
      val r = Atomic(maxValue)
      r.increment()
      expect(r.get).toBe(ev.plus(maxValue, ev.one))
    }

    it("should maybe overflow on min") {
      val r = Atomic(minValue)
      r.decrement()
      expect(r.get).toBe(ev.minus(minValue, ev.one))
    }
  }
}

class AtomicDoubleTest extends AtomicNumberTest[Double, AtomicDouble](
  "AtomicDouble", Atomic.builderFor(0.0), 17.23, Some(Double.NaN), Double.MaxValue, Double.MinValue) {

  describe("AtomicDouble") {
    it("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
      expect(Atomic(Double.MinPositiveValue).get).toBe(Double.MinPositiveValue)
      expect(Atomic(Double.NaN).get.isNaN).toBe(true)
      expect(Atomic(Double.NegativeInfinity).get.isNegInfinity).toBe(true)
      expect(Atomic(Double.PositiveInfinity).get.isPosInfinity).toBe(true)
    }
  }
}

class AtomicFloatTest extends AtomicNumberTest[Float, AtomicFloat](
  "AtomicFloat", Atomic.builderFor(0.0f), 17.23f, Some(Float.NaN), Float.MaxValue, Float.MinValue) {

  describe("AtomicFloat") {
    it("should store MinPositiveValue, NaN, NegativeInfinity, PositiveInfinity") {
      expect(Atomic(Float.MinPositiveValue).get).toBe(Float.MinPositiveValue)
      expect(Atomic(Float.NaN).get.isNaN).toBe(true)
      expect(Atomic(Float.NegativeInfinity).get.isNegInfinity).toBe(true)
      expect(Atomic(Float.PositiveInfinity).get.isPosInfinity).toBe(true)
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
