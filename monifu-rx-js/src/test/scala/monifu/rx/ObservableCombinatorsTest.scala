package monifu.rx

import scala.scalajs.test.JasmineTest
import concurrent.duration._

object ObservableCombinatorsTest extends JasmineTest {
  import monifu.concurrent.Scheduler.Implicits.computation

  describe("Observable.map") {
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

  describe("Observable.filter") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should work") {
      val obs = Observable.fromSequence(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      jasmine.Clock.tick(1)
      expect(obs.value.get.get.get).toBe((1 to 10).filter(_ % 2 == 0).sum)
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).filter(_ => true).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.interval(1.millis).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
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
      expect(effect).toBe((0 until 5).sum)
      expect(errorThrow.getMessage).toBe("test")
      expect(sub.isCanceled).toBe(true)
    }
  }

  describe("Observable.flatMap") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should work") {
      val f = Observable.fromSequence(0 until 100).filter(_ % 5 == 0).flatMap(x => Observable.fromSequence(x until (x + 5)))
        .foldLeft(0)(_ + _).subscribeOn(computation).asFuture

      jasmine.Clock.tick(1)
      expect(f.value.get.get.get).toBe((0 until 100).sum)
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).flatMap(x => Observable.unit(x)).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromSequence(0 until 100).filter(_ % 5 == 0).flatMap { x =>
        if (x < 50) Observable.fromSequence(x until (x + 5)) else throw new RuntimeException("test")
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
      expect(effect).toBe((0 until 50).sum)
      expect(errorThrow.getMessage).toBe("test")
      expect(sub.isCanceled).toBe(true)
    }
  }
}
