package monifu.concurrent.atomic

import scala.scalajs.test.JasmineTest

object AtomicAnyTest extends JasmineTest with ImplicitsLevel2 {

  describe("AtomicAnyTest") {
    it("should implement set()") {
      val ref = Atomic("hello")
      ref.set("world")
      expect(ref.get).toBe("world")
    }
  }
}
