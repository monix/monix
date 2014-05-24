package monifu.concurrent.cancelables

import scala.scalajs.test.JasmineTest

object SingleAssignmentCancelableTest extends JasmineTest {
  describe("SingleAssignmentCancelable") {
    it("should cancel") {
      var effect = 0
      val s = SingleAssignmentCancelable()
      val b = BooleanCancelable { effect += 1 }
      s() = b

      s.cancel()
      expect(s.isCanceled).toBe(true)
      expect(b.isCanceled).toBe(true)
      expect(effect).toBe(1)

      s.cancel()
      expect(effect).toBe(1)
    }

    it("should cancel on single assignment") {
      val s = SingleAssignmentCancelable()
      s.cancel()
      expect(s.isCanceled).toBe(true)

      var effect = 0
      val b = BooleanCancelable { effect += 1 }
      s() = b

      expect(b.isCanceled).toBe(true)
      expect(effect).toBe(1)

      s.cancel()
      expect(effect).toBe(1)
    }

    it("should throw exception on multi assignment") {
      val s = SingleAssignmentCancelable()
      val b1 = BooleanCancelable.alreadyCanceled
      val b2 = BooleanCancelable.alreadyCanceled

      s() = b1
      expect(() => s() = b2).toThrow()
    }

    it("should throw exception on multi assignment when canceled") {
      val s = SingleAssignmentCancelable()
      s.cancel()

      val b1 = BooleanCancelable.alreadyCanceled
      s() = b1

      val b2 = BooleanCancelable.alreadyCanceled
      expect(() => s() = b2).toThrow()
    }
  }
}
