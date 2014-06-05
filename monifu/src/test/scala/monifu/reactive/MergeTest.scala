package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import scala.concurrent.{Future, Await}
import concurrent.duration._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.{Cancel, Continue}

/**
 * Observable.merge is special, gets its own test.
 */
class MergeTest extends FunSpec {
  describe("Observable.merge") {
    it("should work") {
      val result = Observable.from(0 until 100).filter(_ % 5 == 0)
        .mergeMap(x => Observable.from(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      assert(Await.result(result, 4.seconds) === Some((0 until 100).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.mergeMap(x => Observable.unit(x)).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 100).mergeMap { x =>
        if (x < 50) Observable.unit(x) else throw new RuntimeException("test")
      }

      @volatile var sum = 0
      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
          Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(10, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
      assert(sum === (0 until 50).sum)
    }

    it("should generate elements, without ordering guaranteed") {
      val obs = Observable.from(0 until 100).filter(_ % 5 == 0)
        .mergeMap(x => Observable.from(x until (x + 5)))
        .foldLeft(Seq.empty[Int])(_ :+ _)
        .map(_.sorted)
        .asFuture

      val result = Await.result(obs, 4.seconds)
      assert(result === Some(0 until 100))
    }

    it("should satisfy source.filter(p) == source.mergeMap(x => if (p(x)) unit(x) else empty), without ordering") {
      val parent = Observable.from(0 until 1000)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.mergeMap(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty)
        .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

      assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
    }

    it("should satisfy source.map(f) == source.mergeMap(x => unit(x)), without ordering") {
      val parent = Observable.from(0 until 1000)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
      val res2 = parent.mergeMap(x => Observable.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

      assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
    }

    it("should satisfy source.map(f).merge == source.mergeMap(f)") {
      val parent = Observable.from(0 until 1000).filter(_ % 2 == 0)
      val res1 = parent.map(x => Observable.from(x until (x + 2))).merge
        .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
      val res2 = parent.mergeMap(x => Observable.from(x until (x + 2)))
        .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

      assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.from(0 until 1000).doOnComplete(latch.countDown())
        .mergeMap(x => Observable.repeat(x)).take(1000).subscribe()

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should work with Futures") {
      val f = Observable.from(0 until 100).mergeMap(x => Future(x + 1))
        .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
      val result = Await.result(f, 4.seconds)
      assert(result === Some(1 to 100))
    }

    it("should not have concurrency problems, test 1") {
      val f = Observable.from(0 until 1000)
        .observeOn(global)
        .take(100)
        .observeOn(global)
        .mergeMap(x => Observable.range(x, x + 100).observeOn(global).take(10).mergeMap(x => Observable.unit(x).observeOn(global)))
        .foldLeft(Seq.empty[Int])(_:+_)
        .asFuture

      val r = Await.result(f, 20.seconds)
      assert(r.nonEmpty && r.get.size === 100 * 10)
      assert(r.get.sorted === (0 until 1000).take(100).flatMap(x => x until (x + 10)).sorted)
    }

    it("should not have concurrency problems, test 2") {
      val f = Observable.from(0 until 1000)
        .observeOn(global)
        .take(100)
        .observeOn(global)
        .mergeMap(x => Observable.range(x, x + 100).observeOn(global).take(10).mergeMap(x => Observable.unit(x).observeOn(global)))
        .take(100 * 9)
        .foldLeft(Seq.empty[Int])(_:+_)
        .asFuture

      val r = Await.result(f, 20.seconds)
      assert(r.nonEmpty && r.get.size === 100 * 9)
    }
  }
}
