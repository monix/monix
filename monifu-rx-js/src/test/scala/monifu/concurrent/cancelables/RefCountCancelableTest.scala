package monifu.concurrent.cancelables

import scala.scalajs.test.JasmineTest

object RefCountCancelableTest extends JasmineTest {
  describe("RefCountCancelable") {
    it("should cancel without dependent references") {
      var isCanceled = false
      val sub = RefCountCancelable { isCanceled = true }
      sub.cancel()

      expect(sub.isCanceled).toBe(true)
      expect(isCanceled).toBe(true)
    }

    it("should execute onCancel with no active refs available") {
      var isCanceled = false
      val sub = RefCountCancelable { isCanceled = true }

      val s1 = sub.acquire()
      val s2 = sub.acquire()
      s1.cancel()
      s2.cancel()

      expect(isCanceled).toBe(false)
      expect(sub.isCanceled).toBe(false)

      sub.cancel()

      expect(isCanceled).toBe(true)
      expect(sub.isCanceled).toBe(true)
    }

    it("should execute onCancel only after all dependent refs have been canceled") {
      var isCanceled = false
      val sub = RefCountCancelable { isCanceled = true }

      val s1 = sub.acquire()
      val s2 = sub.acquire()
      sub.cancel()

      expect(sub.isCanceled).toBe(true)
      expect(isCanceled).toBe(false)
      s1.cancel()
      expect(isCanceled).toBe(false)
      s2.cancel()
      expect(isCanceled).toBe(true)
    }
  }
}
