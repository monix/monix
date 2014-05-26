package monifu.concurrent.atomic.padded

import scala.scalajs.test.JasmineTest

object PaddedAtomicAnyTest extends JasmineTest {

  describe("AtomicAny") {
    it("should set()") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      r.set("update")
      expect(r.get).toBe("update")
    }

    it("should getAndSet") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      expect(r.getAndSet("update")).toBe("initial")
      expect(r.get).toBe("update")
    }

    it("should compareAndSet") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      expect(r.compareAndSet("initial", "update")).toBe(true)
      expect(r.get).toBe("update")
      expect(r.compareAndSet("initial", "other") ).toBe(false)
      expect(r.get).toBe("update")
      expect(r.compareAndSet("update",  "other") ).toBe(true)
      expect(r.get).toBe("other")
    }

    it("should transform") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      r.transform(s => "updated" + s.dropWhile(_ != ' '))
      expect(r.get).toBe("updated value")
    }

    it("should transformAndGet") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      val value = r.transformAndGet(s => "updated" + s.dropWhile(_ != ' '))
      expect(value).toBe("updated value")
    }

    it("should getAndTransform") {
      val r = Atomic("initial value")
      expect(r()).toBe("initial value")

      val value = r.getAndTransform(s => "updated" + s.dropWhile(_ != ' '))
      expect(value).toBe("initial value")
      expect(r.get).toBe("updated value")
    }

    it("should transformAndExtract") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      val value = r.transformAndExtract { s =>
        val newS = "updated" + s.dropWhile(_ != ' ')
        ("extracted", newS)
      }

      expect(value).toBe("extracted")
      expect(r.get).toBe("updated value")
    }
  }
}
