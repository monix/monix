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

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val latch = new CountDownLatch(1)
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).map(x => x).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        },
        completedFn = () =>
          latch.countDown()
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.interval(1.millis).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      val latch = new CountDownLatch(1)
      val effect = Atomic(Seq.empty[Long])
      @volatile var errorThrow: Throwable = null

      val sub = obs.subscribe(
        nextFn = e => effect.transform(_ :+ e),
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
        }
      )

      latch.await()
      assert(effect.get.sorted === (1 to 5))
      assert(errorThrow.getMessage === "test")
      assert(sub.isCanceled === true)
    }
  }

  describe("Observable.filter") {
    it("should work") {
      val obs = Observable.fromSequence(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      assert(Await.result(obs, 1.second) === Some((1 to 10).filter(_ % 2 == 0).sum))
    }

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val latch = new CountDownLatch(1)
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).filter(x => true).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        },
        completedFn = () =>
          latch.countDown()
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.interval(1.millis).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      val latch = new CountDownLatch(1)
      val effect = Atomic(0L)
      @volatile var errorThrow: Throwable = null

      val sub = obs.subscribe(
        nextFn = e => effect.transform(_ + e),
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
        }
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

    it("should not throw exceptions in subscribe implementations (guideline 6.5)") {
      val latch = new CountDownLatch(1)
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).flatMap(x => Observable.unit(x)).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        },
        completedFn = () =>
          latch.countDown()
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.fromSequence(0 until 100).filter(_ % 5 == 0).flatMap { x =>
        if (x < 50) Observable.fromSequence(x until (x + 5)) else throw new RuntimeException("test")
      }

      val latch = new CountDownLatch(1)
      @volatile var effect = 0
      @volatile var error = ""
      val sub = obs.subscribeOn(computation).subscribe(
        e => { effect += e },
        err => { error = err.getMessage; latch.countDown() }
      )

      latch.await()
      assert(effect === (0 until 50).sum)
      assert(error === "test")
      assert(sub.isCanceled === true)
    }
  }
}