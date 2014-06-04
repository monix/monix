package monifu.concurrent.schedulers

import scala.scalajs.test.JasmineTest
import scala.concurrent.Future
import monifu.concurrent.Scheduler.Implicits.trampoline
import concurrent.duration._
import monifu.reactive._
import monifu.reactive.api.Ack.{Continue, Done}


object ObservableTest extends JasmineTest {
  beforeEach {
    jasmine.Clock.useMock()
  }

	describe("Observable.map") {
    it("should work") {
      val f = Observable.from(0 until 10).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      jasmine.Clock.tick(1)

      expect(f.value.get.get.get.mkString(",")).toBe((1 to 10).mkString(","))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      var result = ""
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          Done
        }
      )

      jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should cancel when downstream has canceled") {
      var wasCompleted = false
      Observable.repeat(1).doOnComplete(wasCompleted = true).map(x => x).take(1000).subscribe()

      jasmine.Clock.tick(1)
      expect(wasCompleted).toBe(true)
    }
  }

  describe("Observable.mergeMap") {
    it("should work") {
      val result2 = 
        Observable.from(0 until 100).filter(_ % 5 == 0)
          .mergeMap(x => Observable.from(x until (x + 5)))
          .foldLeft(0)(_ + _).asFuture

      jasmine.Clock.tick(1)
      expect(result2.value.get.get.get).toBe((0 until 100).sum)
    }
  }
}