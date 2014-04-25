package monifu.concurrent.atomic

import org.scalatest.FunSpec

class AtomicAnyTest extends FunSpec {
  describe("AtomicAny") {
    it("should set()") {
      val r = Atomic("initial")
      assert(r.get === "initial")

      r.set("update")
      assert(r.get === "update")
    }

    it("should getAndSet()") {
      val r = Atomic("initial")
      assert(r.get === "initial")

      assert(r.getAndSet("update") === "initial")
      assert(r.get === "update")
    }

    it("should compareAndSet()") {
      val r = Atomic("initial")
      assert(r.get === "initial")

      assert(r.compareAndSet("initial", "update") === true)
      assert(r.get === "update")
      assert(r.compareAndSet("initial", "other")  === false)
      assert(r.get === "update")
      assert(r.compareAndSet("update",  "other")  === true)
      assert(r.get === "other")
    }

    it("should increment()") {
      val r = Atomic(BigInt(1))
      assert(r.get === BigInt(1))

      r.increment()
      assert(r.get === BigInt(2))
      r.increment(2)
      assert(r.get === BigInt(4))
    }

    it("should decrement()") {
      val r = Atomic(BigInt(100))
      assert(r.get === BigInt(100))

      r.decrement()
      assert(r.get === BigInt(99))
      r.decrement(49)
      assert(r.get === BigInt(50))
    }

    it("should incrementAndGet()") {
      val r = Atomic(BigInt(100))
      assert(r.get === BigInt(100))

      assert(r.incrementAndGet() === 101)
      assert(r.incrementAndGet() === 102)

      assert(r.addAndGet(BigInt(20)) === 122)
      assert(r.addAndGet(BigInt(20)) === 142)
    }

    it("should decrementAndGet()") {
      val r = Atomic(BigInt(100))
      assert(r.get === BigInt(100))

      assert(r.decrementAndGet() === 99)
      assert(r.decrementAndGet() === 98)
      assert(r.subtractAndGet(BigInt(20)) === 78)
      assert(r.subtractAndGet(BigInt(20)) === 58)
    }

    it("should getAndIncrement()") {
      val r = Atomic(BigInt(100))
      assert(r.get === BigInt(100))

      assert(r.getAndIncrement() === 100)
      assert(r.getAndIncrement() === 101)
      assert(r.getAndAdd(BigInt(20)) === 102)
      assert(r.getAndAdd(BigInt(20)) === 122)
    }

    it("should getAndDecrement()") {
      val r = Atomic(BigInt(100))
      assert(r.get === BigInt(100))

      assert(r.getAndDecrement() === 100)
      assert(r.getAndDecrement() === 99)
      assert(r.getAndSubtract(BigInt(20)) === 98)
      assert(r.getAndSubtract(BigInt(20)) === 78)
    }

    it("should transform()") {
      val r = Atomic("initial value")
      assert(r.get === "initial value")

      r.transform(s => "updated" + s.dropWhile(_ != ' '))
      assert(r.get === "updated value")
    }

    it("should transformAndGet()") {
      val r = Atomic("initial value")
      assert(r.get === "initial value")

      val value = r.transformAndGet(s => "updated" + s.dropWhile(_ != ' '))
      assert(value === "updated value")
    }

    it("should getAndTransform()") {
      val r = Atomic("initial value")
      assert(r() === "initial value")

      val value = r.getAndTransform(s => "updated" + s.dropWhile(_ != ' '))
      assert(value === "initial value")
      assert(r.get === "updated value")
    }

    it("should transformAndExtract()") {
      val r = Atomic("initial value")
      assert(r.get === "initial value")

      val value = r.transformAndExtract { s =>
        val newS = "updated" + s.dropWhile(_ != ' ')
        ("extracted", newS)
      }

      assert(value === "extracted")
      assert(r.get === "updated value")
    }
  }
}
