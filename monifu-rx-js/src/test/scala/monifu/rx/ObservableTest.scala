package monifu.rx

import scala.scalajs.test.JasmineTest
import concurrent.duration._
import monifu.concurrent.Scheduler.Implicits.computation

object ObservableTest extends JasmineTest {
  describe("Observable") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should execute Observable.interval()") {
      val f = Observable.interval(10.millis)
        .takeWhile(_ < 10)
        .foldLeft(0L)(_ + _)
        .asFuture

      for (i <- 0 until 10) {
        jasmine.Clock.tick(10)
        val value = f.value.flatMap(_.toOption.flatten).getOrElse(0L)
        expect(value).toBe(0L)
      }

      jasmine.Clock.tick(10)
      val value = f.value.flatMap(_.toOption.flatten).getOrElse(0L)
      expect(value).toBe(9 * 5)
    }
  }
}
