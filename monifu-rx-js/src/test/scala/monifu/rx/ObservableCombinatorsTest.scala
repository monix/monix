package monifu.rx

import scala.scalajs.test.JasmineTest
import monifu.concurrent.Scheduler.Implicits.trampoline

object ObservableCombinatorsTest extends JasmineTest {

  describe("Observable.map") {
    it("should work") {
      val obs = Observable.fromTraversable(0 until 10).map(x => x + 1)
        .foldLeft(0)(_ + _).asFuture

      expect(obs.value.get.get.get).toBe(55)
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.map(x => x).subscribeUnit(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromTraversable(0 until 100).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      var effect = 0L
      var errorThrow: Throwable = null

      obs.subscribeUnit(
        elem => {
          effect = effect + elem
        },
        ex => {
          errorThrow = ex
        }
      )

      expect(effect).toBe((1 to 5).sum)
      expect(errorThrow.getMessage).toBe("test")
    }
  }

  describe("Observable.filter") {
    it("should work") {
      val obs = Observable.fromTraversable(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      expect(obs.value.get.get.get).toBe((1 to 10).filter(_ % 2 == 0).sum)
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.filter(_ => true).subscribeUnit(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromTraversable(0 until 100).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      var effect = 0L
      var errorThrow: Throwable = null

      obs.subscribeUnit(
        elem => {
          effect = effect + elem
        },
        ex => {
          errorThrow = ex
        }
      )

      expect(effect).toBe((0 until 5).sum)
      expect(errorThrow.getMessage).toBe("test")
    }
  }

  describe("Observable.flatMap") {
    it("should work") {
      val f = Observable.fromTraversable(0 until 100).filter(_ % 5 == 0).flatMap(x => Observable.fromTraversable(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture
      expect(f.value.get.get.get).toBe((0 until 100).sum)
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.flatMap(x => Observable.unit(x)).subscribeUnit(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      expect(result).toBe("Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromTraversable(0 until 100).flatMap { x =>
        if (x < 50) Observable.unit(x) else throw new RuntimeException("test")
      }

      var effect = 0L
      var errorThrow: Throwable = null

      obs.subscribeUnit(
        elem => {
          effect = effect + elem
        },
        ex => {
          errorThrow = ex
        }
      )

      expect(effect).toBe((0 until 50).sum)
      expect(errorThrow.getMessage).toBe("test")
    }

    it("should satisfy source.filter(p) == source.flatMap(x => if (p(x)) unit(x) else empty)") {
      val parent = Observable.fromTraversable(0 until 1000)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      expect(res1.value.get.get.get.sum).toBe(res2.value.get.get.get.sum)
    }

    it("should satisfy source.map(f) == source.flatMap(x => unit(x))") {
      val parent = Observable.fromTraversable(0 until 50)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      expect(res1.value.get.get.get.sum).toBe(res2.value.get.get.get.sum)
    }

    it("should satisfy source.map(f).flatten == source.flatMap(f)") {
      val parent = Observable.fromTraversable(0 until 10).filter(_ % 2 == 0)
      val res1 = parent.map(x => Observable.fromTraversable(x until (x + 2))).flatten.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.fromTraversable(x until (x + 2))).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      expect(res1.value.get.get.get.sum).toBe(res2.value.get.get.get.sum)
    }
  }

  describe("Observable.zip") {
    it("should work synchronously") {
      val obs1 = Observable.fromTraversable(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromTraversable(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.fromTraversable(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.fromTraversable(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      val f = finalObs.asFuture

      val length = f.value.get.get.get.length
      val sum = f.value.get.get.get.map { case (a,b,c,d) => a + b - c - d }.sum

      expect(sum).toBe(0)
      expect(length).toBe(10)
    }

    it("should work asynchronously") {
      val obs1 = Observable.fromTraversable(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromTraversable(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.fromTraversable(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.fromTraversable(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      val f = finalObs.asFuture

      val length = f.value.get.get.get.length
      val sum = f.value.get.get.get.map { case (a,b,c,d) => a + b - c - d }.sum

      expect(sum).toBe(0)
      expect(length).toBe(10)
    }
  }
}
