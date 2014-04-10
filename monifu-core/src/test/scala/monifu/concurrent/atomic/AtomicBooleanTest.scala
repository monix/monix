package monifu.concurrent.atomic

import org.scalatest.FunSpec

class AtomicBooleanTest extends FunSpec {
  describe("AtomicBoolean") {
    it("should set()") {
      val r = Atomic(initialValue = true)
      assert(r() === true)
      r.set(update = false)
      assert(r() === false)
      r.set(update = true)
      assert(r() === true)
    }

    it("should getAndSet()") {
      val r = Atomic(initialValue = true)
      assert(r.get === true)

      assert(r.getAndSet(update = false) === true)
      assert(r.get === false)
    }

    it("should compareAndSet()") {
      val r = Atomic(initialValue = true)
      assert(r.get === true)

      assert(r.compareAndSet(expect = true, update = false) === true)
      assert(r.get === false)
      assert(r.compareAndSet(expect = true, update = true) === false)
      assert(r.get === false)
      assert(r.compareAndSet(expect = false, update = true) === true)
      assert(r.get === true)
    }
  }
}
