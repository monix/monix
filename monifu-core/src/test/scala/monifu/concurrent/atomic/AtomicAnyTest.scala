package monifu.concurrent.atomic

import monifu.test.MonifuTest

class AtomicAnyTest extends MonifuTest {
  describe("AtomicAny") {
    it("should set()") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      r.set("update")
      expect(r.get).toBe("update")
    }

    it("should getAndSet()") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      expect(r.getAndSet("update")).toBe("initial")
      expect(r.get).toBe("update")
    }

    it("should compareAndSet()") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      expect(r.compareAndSet("initial", "update")).toBe(true)
      expect(r.get).toBe("update")
      expect(r.compareAndSet("initial", "other") ).toBe(false)
      expect(r.get).toBe("update")
      expect(r.compareAndSet("update",  "other") ).toBe(true)
      expect(r.get).toBe("other")
    }

    it("should increment()") {
      val r = Atomic(BigInt(1))
      expect(r.get).toBe(BigInt(1))

      r.increment()
      expect(r.get).toBe(BigInt(2))
      r.increment(2)
      expect(r.get).toBe(BigInt(4))
    }

    it("should decrement()") {
      val r = Atomic(BigInt(100))
      expect(r.get).toBe(BigInt(100))

      r.decrement()
      expect(r.get).toBe(BigInt(99))
      r.decrement(49)
      expect(r.get).toBe(BigInt(50))
    }

    it("should incrementAndGet()") {
      val r = Atomic(BigInt(100))
      expect(r.get).toBe(BigInt(100))

      expect(r.incrementAndGet()).toBe(101)
      expect(r.incrementAndGet()).toBe(102)

      expect(r.addAndGet(BigInt(20))).toBe(122)
      expect(r.addAndGet(BigInt(20))).toBe(142)
    }

    it("should decrementAndGet()") {
      val r = Atomic(BigInt(100))
      expect(r.get).toBe(BigInt(100))

      expect(r.decrementAndGet()).toBe(99)
      expect(r.decrementAndGet()).toBe(98)
      expect(r.subtractAndGet(BigInt(20))).toBe(78)
      expect(r.subtractAndGet(BigInt(20))).toBe(58)
    }

    it("should getAndIncrement()") {
      val r = Atomic(BigInt(100))
      expect(r.get).toBe(BigInt(100))

      expect(r.getAndIncrement()).toBe(100)
      expect(r.getAndIncrement()).toBe(101)
      expect(r.getAndAdd(BigInt(20))).toBe(102)
      expect(r.getAndAdd(BigInt(20))).toBe(122)
    }

    it("should getAndDecrement()") {
      val r = Atomic(BigInt(100))
      expect(r.get).toBe(BigInt(100))

      expect(r.getAndDecrement()).toBe(100)
      expect(r.getAndDecrement()).toBe(99)
      expect(r.getAndSubtract(BigInt(20))).toBe(98)
      expect(r.getAndSubtract(BigInt(20))).toBe(78)
    }

    it("should transform()") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      r.transform(s => "updated" + s.dropWhile(_ != ' '))
      expect(r.get).toBe("updated value")
    }

    it("should transformAndGet()") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      val value = r.transformAndGet(s => "updated" + s.dropWhile(_ != ' '))
      expect(value).toBe("updated value")
    }

    it("should getAndTransform()") {
      val r = Atomic("initial value")
      expect(r()).toBe("initial value")

      val value = r.getAndTransform(s => "updated" + s.dropWhile(_ != ' '))
      expect(value).toBe("initial value")
      expect(r.get).toBe("updated value")
    }

    it("should transformAndExtract()") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      val value = r.transformAndExtract { s =>
        val newS = "updated" + s.dropWhile(_ != ' ')
        (newS, "extracted")
      }

      expect(value).toBe("extracted")
      expect(r.get).toBe("updated value")
    }
  }
}
