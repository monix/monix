package monifu.rx

import monifu.concurrent.Scheduler.Implicits.global
import monifu.rx.api._
import scala.language.higherKinds
import scala.scalajs.test.JasmineTest

class GenericObservableOperatorsTest[Observable[+T] <: ObservableLike[T, Observable]](builder: ObservableTypeClass[Observable], isAsync: Boolean = true)
  extends JasmineTest {

  describe("Observable.map") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should work") {
      val f = builder.fromTraversable(0 until 100).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      if (isAsync) jasmine.Clock.tick(1)
      val result = f.value.get.get.get.sum
      expect(result).toBe((1 until 101).sum)
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = builder.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""

      obs.map(x => x).subscribeUnit(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      if (isAsync) jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = builder.fromTraversable(0 until 10000).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      var errorThrow: Throwable = null

      obs.map(x => x).subscribeUnit(
        nextFn = _ => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          errorThrow = ex
        }
      )

      if (isAsync) jasmine.Clock.tick(1)
      expect(errorThrow.getMessage).toBe("test")
    }
  }

  describe("Observable.filter") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should work") {
      val f = builder.fromTraversable(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture

      if (isAsync) jasmine.Clock.tick(1)
      expect(f.value.get.get.get).toBe((1 to 10).filter(_ % 2 == 0).sum)
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = builder.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""

      obs.filter(_ % 2 == 0).subscribeUnit(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      if (isAsync) jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = builder.fromTraversable(0 until 100).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      var sum = 0
      var errorThrow: Throwable = null

      obs.map(x => x).subscribeUnit(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
        },
        errorFn = ex => {
          errorThrow = ex
        }
      )

      if (isAsync) jasmine.Clock.tick(1)
      expect(errorThrow.getMessage).toBe("test")
      expect(sum).toBe((0 until 5).sum)
    }
  }

  describe("Observable.flatMap") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should work") {
      val result = builder.fromTraversable(0 until 100).filter(_ % 5 == 0)
        .flatMap(x => builder.fromTraversable(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      if (isAsync) jasmine.Clock.tick(1)
      expect(result.value.get.get.get).toBe((0 until 100).sum)
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = builder.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""

      obs.flatMap(x => builder.unit(x)).subscribeUnit(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      if (isAsync) jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = builder.fromTraversable(0 until 100).flatMap { x =>
        if (x < 50) builder.unit(x) else throw new RuntimeException("test")
      }

      var sum = 0
      var errorThrow: Throwable = null      

      obs.map(x => x).subscribeUnit(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
        },
        errorFn = ex => {
          errorThrow = ex
        }
      )

      if (isAsync) jasmine.Clock.tick(1)
      expect(errorThrow.getMessage).toBe("test")
      expect(sum).toBe((0 until 50).sum)
    }

    it("should generate elements in order") {
      val obs = builder.fromTraversable(0 until 100).filter(_ % 5 == 0)
        .flatMap(x => builder.fromTraversable(x until (x + 5)))
        .foldLeft(Seq.empty[Int])(_ :+ _)
        .asFuture

      if (isAsync) jasmine.Clock.tick(1)
      val result = obs.value.get.get.get
      expect(result.mkString("-")).toBe((0 until 100).mkString("-"))
    }

    it("should satisfy source.filter(p) == source.flatMap(x => if (p(x)) unit(x) else empty)") {
      val parent = builder.fromTraversable(0 until 100)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => if (x % 5 == 0) builder.unit(x) else builder.empty).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      if (isAsync) jasmine.Clock.tick(1)
      expect(res1.value.get.get.get.mkString("-")).toBe(res2.value.get.get.get.mkString("-"))
    }

    it("should satisfy source.map(f) == source.flatMap(x => unit(x))") {
      val parent = builder.fromTraversable(0 until 50)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => builder.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      if (isAsync) jasmine.Clock.tick(1)
      expect(res1.value.get.get.get.mkString("-")).toBe(res2.value.get.get.get.mkString("-"))
    }

    it("should satisfy source.map(f).flatten == source.flatMap(f)") {
      val parent = builder.fromTraversable(0 until 10).filter(_ % 2 == 0)
      val res1 = parent.map(x => builder.fromTraversable(x until (x + 2))).flatten.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => builder.fromTraversable(x until (x + 2))).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      if (isAsync) jasmine.Clock.tick(1)
      expect(res1.value.get.get.get.mkString("-")).toBe(res2.value.get.get.get.mkString("-"))
    }
  }
}
