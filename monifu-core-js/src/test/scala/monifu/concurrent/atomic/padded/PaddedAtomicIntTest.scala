package monifu.concurrent.atomic.padded

import scala.scalajs.test.JasmineTest

object PaddedAtomicIntTest extends JasmineTest {
  describe("AtomicInt") {
    it("should set") {
      val r = Atomic(11)
      expect(r.get).toBe(11)

      r.set(100)
      expect(r.get).toBe(100)
    }

    it("should getAndSet") {
      val r = Atomic(100)
      expect(r.getAndSet(200)).toBe(100)
      expect(r.getAndSet(0)).toBe(200)
      expect(r.get).toBe(0)
    }

    it("should compareAndSet") {
      val r = Atomic(0)

      expect(r.compareAndSet(0, 100)).toBe(true)
      expect(r.get).toBe(100)
      expect(r.compareAndSet(0, 200)).toBe(false)
      expect(r.get).toBe(100)
      expect(r.compareAndSet(100, 200)).toBe(true)
      expect(r.get).toBe(200)
    }

    it("should increment") {
      val r = Atomic(0)
      expect(r.get).toBe(0)

      r.increment()
      expect(r.get).toBe(1)
      r.increment()
      expect(r.get).toBe(2)
    }

    it("should incrementNumber") {
      val r = Atomic(0)
      expect(r.get).toBe(0)

      r.increment(2)
      expect(r.get).toBe(2)

      r.increment(Int.MaxValue - 2)
      expect(r.get).toBe(Int.MaxValue)
    }

    it("should decrement") {
      val r = Atomic(1)
      expect(r.get).toBe(1)

      r.decrement()
      expect(r.get).toBe(0)
      r.decrement()
      expect(r.get).toBe(-1)
      r.decrement()
      expect(r.get).toBe(-2)
    }

    it("should decrementNumber") {
      val r = Atomic(1)
      expect(r.get).toBe(1)

      r.decrement(3)
      expect(r.get).toBe(-2)
    }

    it("should incrementAndGet") {
      val r = Atomic(Int.MaxValue - 2)
      expect(r.incrementAndGet()).toBe(Int.MaxValue - 1)
      expect(r.incrementAndGet()).toBe(Int.MaxValue)
    }

    it("should getAndIncrement") {
      val r = Atomic(126)
      expect(r.getAndIncrement()).toBe(126)
      expect(r.getAndIncrement()).toBe(127)
      expect(r.getAndIncrement()).toBe(128)
      expect(r.get).toBe(129)
    }

    it("should decrementAndGet") {
      val r = Atomic(Int.MinValue + 2)
      expect(r.decrementAndGet()).toBe(Int.MinValue + 1)
      expect(r.decrementAndGet()).toBe(Int.MinValue)
    }

    it("should getAndDecrement") {
      val r = Atomic(Int.MinValue + 2)
      expect(r.getAndDecrement()).toBe(Int.MinValue + 2)
      expect(r.getAndDecrement()).toBe(Int.MinValue + 1)
      expect(r.getAndDecrement()).toBe(Int.MinValue)
    }

    it("should incrementAndGetNumber") {
      val r = Atomic(Int.MaxValue - 4)
      expect(r.incrementAndGet(2)).toBe(Int.MaxValue - 2)
      expect(r.incrementAndGet(2)).toBe(Int.MaxValue)
    }

    it("should getAndIncrementNumber") {
      val r = Atomic(Int.MaxValue - 4)
      expect(r.getAndIncrement(2)).toBe(Int.MaxValue - 4)
      expect(r.getAndIncrement(2)).toBe(Int.MaxValue - 2)
      expect(r.getAndIncrement(2)).toBe(Int.MaxValue)
    }

    it("should decrementAndGetNumber") {
      val r = Atomic(Int.MaxValue)
      expect(r.decrementAndGet(2)).toBe(Int.MaxValue - 2)
      expect(r.decrementAndGet(2)).toBe(Int.MaxValue - 4)
      expect(r.decrementAndGet(2)).toBe(Int.MaxValue - 6)
    }

    it("should getAndDecrementNumber") {
      val r = Atomic(10)
      expect(r.getAndDecrement(2)).toBe(10)
      expect(r.getAndDecrement(2)).toBe(8)
      expect(r.getAndDecrement(2)).toBe(6)
      expect(r.getAndDecrement(2)).toBe(4)
      expect(r.getAndDecrement(2)).toBe(2)
    }
  }
}
