package monifu.rx

import scala.scalajs.test.JasmineTest
import concurrent.duration._

object ObservableCombinatorsTest extends JasmineTest {
  describe("Observable.map") {
    import monifu.concurrent.Scheduler.Implicits.computation

    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should work") {
      val obs = Observable.fromSequence(0 until 10).map(x => x + 1)
        .foldLeft(0)(_ + _).asFuture

      jasmine.Clock.tick(1)
      expect(obs.value.get.get.get).toBe(55)
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).map(x => x).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.interval(1.millis).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      var effect = 0L
      var errorThrow: Throwable = null

      val sub = obs.subscribe(
        elem => {
          effect = effect + elem
        },
        ex => {
          errorThrow = ex
        }
      )

      jasmine.Clock.tick(10)
      expect(effect).toBe((1 to 5).sum)
      expect(errorThrow.getMessage).toBe("test")
      expect(sub.isCanceled).toBe(true)
    }
  }
}
