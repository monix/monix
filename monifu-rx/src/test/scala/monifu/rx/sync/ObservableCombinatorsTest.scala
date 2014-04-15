package monifu.rx.sync

import org.scalatest.FunSpec
import concurrent.duration._
import scala.concurrent.Await

class ObservableCombinatorsTest extends FunSpec {
  import monifu.concurrent.Scheduler.Implicits.global

  describe("rx.sync.Observable.map") {
    it("should work") {
      val obs = Observable.fromTraversable(0 until 10).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(obs, 1.second) === Some(1 until 11))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.map(x => x).subscribeUnit(
        nextFn = _ => (),
        errorFn = ex => result = ex.getMessage
      )

      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromTraversable(0 until 10000).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      var errorThrow: Throwable = null
      val sub = obs.subscribeUnit(
        nextFn = e => (),
        errorFn = ex => errorThrow = ex
      )

      assert(errorThrow.getMessage === "test")
      assert(sub.isCanceled === true)
    }
  }

  describe("rx.sync.Observable.filter") {
    it("should work") {
      val obs = Observable.fromTraversable(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      assert(Await.result(obs, 1.second) === Some((1 to 10).filter(_ % 2 == 0).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      val sub = obs.filter(x => true).subscribeUnit(
        nextFn = _ => (),
        errorFn = ex => result = ex.getMessage
      )

      assert(result === "Test exception")
      assert(sub.isCanceled)
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromTraversable(0 until 100).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      var effect = 0L
      var errorThrow: Throwable = null

      val sub = obs.subscribeUnit(
        e => effect = effect + e,
        ex => errorThrow = ex
      )

      assert(effect === (0 until 5).sum)
      assert(errorThrow.getMessage === "test")
      assert(sub.isCanceled === true)
    }
  }

  describe("rx.sync.Observable.flatMap") {
    it("should work") {
      val result = Observable.fromTraversable(0 until 100).filter(_ % 5 == 0).flatMap(x => Observable.fromTraversable(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      assert(Await.result(result, 1.second) === Some((0 until 100).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      val sub = obs.flatMap(x => Observable.unit(x)).subscribeUnit(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      assert(result === "Test exception")
      assert(sub.isCanceled === true)
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromTraversable(0 until 100).flatMap { x =>
        if (x < 50) Observable.unit(x) else throw new RuntimeException("test")
      }

      var effect = 0
      var error = ""

      obs.subscribeUnit(
        e => { effect += e },
        err => { error = err.getMessage }
      )

      assert(effect === (0 until 50).sum)
      assert(error === "test")
    }

    it("should satisfy source.filter(p) == source.flatMap(x => if (p(x)) unit(x) else empty)") {
      val parent = Observable.fromTraversable(0 until 1000)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }

    it("should satisfy source.map(f) == source.flatMap(x => unit(x))") {
      val parent = Observable.fromTraversable(0 until 50)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }

    it("should satisfy source.map(f).flatten == source.flatMap(f)") {
      val parent = Observable.fromTraversable(0 until 10).filter(_ % 2 == 0)
      val res1 = parent.map(x => Observable.fromTraversable(x until (x + 2))).flatten.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.fromTraversable(x until (x + 2))).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }
  }

  describe("rx.sync.Observable.zip") {
    it("should work") {
      val obs1 = Observable.fromTraversable(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromTraversable(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.fromTraversable(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.fromTraversable(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      var list = Seq.empty[(Long, Long, Long, Long)]
      finalObs.subscribeUnit { l => list = l }

      assert(list === 0.until(20,2).map(x => (x,x,x,x)))
    }
  }

}