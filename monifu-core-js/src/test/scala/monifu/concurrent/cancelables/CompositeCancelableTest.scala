package monifu.concurrent.cancelables

import scala.scalajs.test.JasmineTest
import monifu.concurrent.Cancelable

object CompositeCancelableTest extends JasmineTest {
  describe("CompositeCancelable") {
    it("should cancel") {
      val s = CompositeCancelable()
      val b1 = Cancelable()
      val b2 = Cancelable()
      s += b1
      s += b2
      s.cancel()

      expect(s.isCanceled).toBe(true)
      expect(b1.isCanceled).toBe(true)
      expect(b2.isCanceled).toBe(true)
    }

    it("should cancel on assignment after being canceled") {
      val s = CompositeCancelable()
      val b1 = Cancelable()
      s += b1
      s.cancel()

      val b2 = Cancelable()
      s += b2

      expect(s.isCanceled).toBe(true)
      expect(b1.isCanceled).toBe(true)
      expect(b2.isCanceled).toBe(true)
    }
  }
}
