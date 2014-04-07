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
}