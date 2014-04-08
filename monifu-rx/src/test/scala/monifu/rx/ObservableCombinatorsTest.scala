package monifu.rx

import org.scalatest.FunSpec
import scala.concurrent.Await
import concurrent.duration._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.concurrent.atomic.Atomic

class ObservableCombinatorsTest extends FunSpec {
  import monifu.concurrent.Scheduler.Implicits.computation

  describe("Observable.map") {
    it("should work") {
      val obs = Observable.fromSequence(0 until 10).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(obs, 1.second) === Some(1 until 11))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val latch = new CountDownLatch(1)
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).map(x => x).doOnCancel(latch.countDown()).subscribe(
        nextFn = _ => (),
        errorFn = ex =>
          result = ex.getMessage
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.interval(1.millis).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      val latch = new CountDownLatch(1)
      @volatile var errorThrow: Throwable = null

      val sub = obs.doOnCancel(latch.countDown()).subscribe(
        nextFn = e => (),
        errorFn = ex => errorThrow = ex
      )

      latch.await()
      assert(errorThrow.getMessage === "test")
      assert(sub.isCanceled === true)
    }
  }

  describe("Observable.filter") {
    it("should work") {
      val obs = Observable.fromSequence(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      assert(Await.result(obs, 1.second) === Some((1 to 10).filter(_ % 2 == 0).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val latch = new CountDownLatch(1)
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      val sub = obs.subscribeOn(computation).filter(x => true).doOnCancel(latch.countDown()).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
      assert(sub.isCanceled)
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.interval(1.millis).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      val latch = new CountDownLatch(1)
      val effect = Atomic(0L)
      @volatile var errorThrow: Throwable = null

      val sub = obs.doOnCancel(latch.countDown()).subscribe(
        nextFn = e => effect.transform(_ + e),
        errorFn = ex =>
          errorThrow = ex
      )

      latch.await()
      assert(effect.get === (0 until 5).sum)
      assert(errorThrow.getMessage === "test")
      assert(sub.isCanceled === true)
    }
  }

  describe("Observable.flatMap") {
    it("should work") {
      val result = Observable.fromSequence(0 until 100).filter(_ % 5 == 0).flatMap(x => Observable.fromSequence(x until (x + 5)))
        .foldLeft(0)(_ + _).subscribeOn(computation).asFuture

      assert(Await.result(result, 1.second) === Some((0 until 100).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val latch = new CountDownLatch(1)
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      val sub = obs.subscribeOn(computation).flatMap(x => Observable.unit(x)).doOnCancel(latch.countDown()).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
      assert(sub.isCanceled === true)
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromSequence(0 until 100).flatMap { x =>
        if (x < 50) Observable.unit(x) else throw new RuntimeException("test")
      }

      val latch = new CountDownLatch(1)
      @volatile var effect = 0
      @volatile var error = ""
      val sub = obs.subscribeOn(computation).doOnCancel(latch.countDown()).subscribe(
        e => { effect += e },
        err => { error = err.getMessage }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(effect === (0 until 50).sum)
      assert(error === "test")
      assert(sub.isCanceled === true)
    }

    it("should satisfy source.filter(p) == source.flatMap(x => if (p(x)) unit(x) else empty)") {
      val parent = Observable.fromSequence(0 until 1000)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }

    it("should satisfy source.map(f) == source.flatMap(x => unit(x))") {
      val parent = Observable.fromSequence(0 until 50)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }

    it("should satisfy source.map(f).flatten == source.flatMap(f)") {
      val parent = Observable.fromSequence(0 until 10).filter(_ % 2 == 0)
      val res1 = parent.map(x => Observable.fromSequence(x until (x + 2))).flatten.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.fromSequence(x until (x + 2))).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }
  }

  describe("Observable.zip") {
    it("should work single-threaded") {
      val obs1 = Observable.fromSequence(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromSequence(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.fromSequence(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.fromSequence(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      var list = Seq.empty[(Long, Long, Long, Long)]
      val latch = new CountDownLatch(1)
      val sub = finalObs.doOnCancel(latch.countDown()).subscribe { l => list = l }

      latch.await(1, TimeUnit.SECONDS)
      assert(list === 0.until(20,2).map(x => (x,x,x,x)))
      assert(sub.isCanceled === true)
    }

    it("should work multi-threaded") {
      val obs1 = Observable.fromSequence(0 until 100).filter(_ % 2 == 0).map(_.toLong).subscribeOn(computation)
      val obs2 = Observable.fromSequence(0 until 1000).map(_ * 2).map(_.toLong).subscribeOn(computation)
      val obs3 = Observable.fromSequence(0 until 100).map(_ * 2).map(_.toLong).subscribeOn(computation)
      val obs4 = Observable.fromSequence(0 until 1000).filter(_ % 2 == 0).map(_.toLong).subscribeOn(computation)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      var list = Seq.empty[(Long, Long, Long, Long)]
      val latch = new CountDownLatch(1)
      val sub = finalObs.doOnCancel(latch.countDown()).subscribe { l => list = l }

      latch.await()
      assert(list === 0.until(20,2).map(x => (x,x,x,x)))
      assert(sub.isCanceled === true)
    }
  }
}