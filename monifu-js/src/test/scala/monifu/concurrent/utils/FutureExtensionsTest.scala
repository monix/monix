package monifu.concurrent.utils

import scala.scalajs.test.JasmineTest
import scala.concurrent.Future
import concurrent.duration._
import monifu.concurrent.extensions._

object FutureExtensionsTest extends JasmineTest {
  import monifu.concurrent.Scheduler.Implicits.global

  describe("FutureExtensions") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should do delayedResult") {
      val f = Future.delayedResult(50.millis)("TICK")
      jasmine.Clock.tick(40)

      expect(f.value.flatMap(_.toOption).getOrElse("")).toBe("")
      jasmine.Clock.tick(10)
      expect(f.value.flatMap(_.toOption).getOrElse("")).toBe("TICK")
    }

    it("should succeed on withTimeout") {
      val f = Future.delayedResult(50.millis)("Hello world!")
      val t = f.withTimeout(300.millis)

      jasmine.Clock.tick(50)
      expect(t.value.get.get).toBe("Hello world!")
    }

    it("should fail on withTimeout") {
      val f = Future.delayedResult(1.second)("Hello world!")
      val t = f.withTimeout(50.millis)

      jasmine.Clock.tick(50)
      expect(t.value.get.isFailure).toBe(true)
    }
  }
}
