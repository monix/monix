package monifu.concurrent.atomic

import monifu.test.MonifuTest

class AtomicBooleanTest extends MonifuTest {
  describe("AtomicBoolean") {
    it("should set()") {
      val r = Atomic(initialValue = true)
      expect(r()).toBe(true)
      r.set(update = false)
      expect(r()).toBe(false)
      r.set(update = true)
      expect(r()).toBe(true)
    }

    it("should getAndSet()") {
      val r = Atomic(initialValue = true)
      expect(r.get).toBe(true)

      expect(r.getAndSet(update = false)).toBe(true)
      expect(r.get).toBe(false)
    }

    it("should compareAndSet()") {
      val r = Atomic(initialValue = true)
      expect(r.get).toBe(true)

      expect(r.compareAndSet(expect = true, update = false)).toBe(true)
      expect(r.get).toBe(false)
      expect(r.compareAndSet(expect = true, update = true)).toBe(false)
      expect(r.get).toBe(false)
      expect(r.compareAndSet(expect = false, update = true)).toBe(true)
      expect(r.get).toBe(true)
    }
  }
}
