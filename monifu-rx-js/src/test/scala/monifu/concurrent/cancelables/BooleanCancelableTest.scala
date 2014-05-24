package monifu.concurrent.cancelables

import scala.scalajs.test.JasmineTest

object BooleanCancelableTest extends JasmineTest {
  describe("Cancelable") {
    it("should cancel()") {
      var effect = 0
      val sub = BooleanCancelable(effect += 1)
      expect(effect).toBe(0)
      expect(sub.isCanceled).toBe(false)

      sub.cancel()
      expect(effect).toBe(1)
      expect(sub.isCanceled).toBe(true)

      sub.cancel()
      expect(effect).toBe(1)
      expect(sub.isCanceled).toBe(true)
    }
  }
}
