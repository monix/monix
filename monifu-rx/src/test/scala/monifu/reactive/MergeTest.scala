package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import scala.concurrent.{Future, Await}
import concurrent.duration._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.{Cancel, Continue}
import scala.util.Random
import monifu.concurrent.atomic.Atomic
import scala.Some
import monifu.reactive.api.BufferPolicy.Unbounded

/**
 * Observable.merge can have potentially severe concurrency problems,
 * hence the comprehensive, long-running tests.
 */
class MergeTest extends FunSpec {
  describe("Observable.merge(BackPressured)") {
    it("should work") {
      for (_ <- 0 until 10000) {
        val result = Observable.from(0 until 100).filter(_ % 5 == 0)
          .mergeMap(x => Observable.from(x until (x + 5)))
          .foldLeft(0)(_ + _).asFuture

        assert(Await.result(result, 4.seconds) === Some((0 until 100).sum))
      }
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      for (_ <- 0 until 1000) {
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
    }

    it("should protect calls to user code (guideline 6.4)") {
      for (_ <- 0 until 1000) {
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
      }
    }

    it("should generate elements, without ordering guaranteed") {
      for (_ <- 0 until 1000) {
        val obs = Observable.from(0 until 100).filter(_ % 5 == 0)
          .mergeMap(x => Observable.from(x until (x + 5)))
          .foldLeft(Seq.empty[Int])(_ :+ _)
          .map(_.sorted)
          .asFuture

        val result = Await.result(obs, 4.seconds)
        assert(result === Some(0 until 100))
      }
    }

    it("should satisfy source.filter(p) == source.mergeMap(x => if (p(x)) unit(x) else empty), without ordering") {
      for (_ <- 0 until 100) {
        val parent = Observable.from(0 until 1000)
        val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
        val res2 = parent.mergeMap(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty)
          .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

        assert(Await.result(res1, 10.seconds) === Await.result(res2, 4.seconds))
      }
    }

    it("should satisfy source.map(f) == source.mergeMap(x => unit(x)), without ordering") {
      for (_ <- 0 until 100) {
        val parent = Observable.from(0 until 1000)
        val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
        val res2 = parent.mergeMap(x => Observable.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

        assert(Await.result(res1, 10.seconds) === Await.result(res2, 4.seconds))
      }
    }

    it("should satisfy source.map(f).merge == source.mergeMap(f)") {
      for (_ <- 0 until 100) {
        val parent = Observable.from(0 until 1000).filter(_ % 2 == 0)
        val res1 = parent.map(x => Observable.from(x until (x + 2))).merge
          .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
        val res2 = parent.mergeMap(x => Observable.from(x until (x + 2)))
          .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

        assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
      }
    }

    it("should cancel when downstream has canceled") {
      for (_ <- 0 until 100) {
        val latch = new CountDownLatch(1)
        Observable.from(0 until 1000).doOnComplete(latch.countDown())
          .mergeMap(x => Observable.repeat(x)).take(1000).subscribe()

        assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
      }
    }

    it("should work with Futures") {
      for (_ <- 0 until 100) {
        val f = Observable.from(0 until 100).mergeMap(x => Future(x + 1))
          .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
        val result = Await.result(f, 4.seconds)
        assert(result === Some(1 to 100))
      }
    }

    it("should not have concurrency problems, test 1") {
      for (_ <- 0 until 100) {
        val f = Observable.from(0 until 1000)
          .observeOn(global)
          .take(100)
          .observeOn(global)
          .mergeMap(x => Observable.range(x, x + 100).observeOn(global).take(10).mergeMap(x => Observable.unit(x).observeOn(global)))
          .foldLeft(Seq.empty[Int])(_ :+ _)
          .asFuture

        val r = Await.result(f, 20.seconds)
        assert(r.nonEmpty && r.get.size === 100 * 10)
        assert(r.get.sorted === (0 until 1000).take(100).flatMap(x => x until (x + 10)).sorted)
      }
    }

    it("should not have concurrency problems, test 2") {
      for (_ <- 0 until 100) {
        val f = Observable.from(0 until 1000)
          .observeOn(global)
          .take(100)
          .observeOn(global)
          .mergeMap(x => Observable.range(x, x + 100).observeOn(global).take(10).mergeMap(x => Observable.unit(x).observeOn(global)))
          .take(100 * 9)
          .foldLeft(Seq.empty[Int])(_ :+ _)
          .asFuture

        val r = Await.result(f, 20.seconds)
        assert(r.nonEmpty && r.get.size === 100 * 9)
      }
    }

    it("should work with random stuff") {
      for (repeats <- 0 until 5) {
        val streamLengths = (0 until 5).map(_ => Random.nextInt(100000))
        val completed = new CountDownLatch(2)

        val result = Observable.from(streamLengths)
          .doOnComplete(completed.countDown())
          .mergeMap(x => Observable.range(0, x))
          .sum
          .doOnComplete(completed.countDown())
          .asFuture

        assert(Await.result(result, 20.seconds) === Some(streamLengths.flatMap(x => 0 until x).sum))
        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      }
    }

    it("should work with never ending streams") {
      val completed = new CountDownLatch(2)
      val result = Observable.repeat(1)
        .doOnComplete(completed.countDown())
        .mergeMap(_ => Observable.repeat(2))
        .doOnComplete(completed.countDown())
        .take(100000).sum.asFuture

      assert(Await.result(result, 20.seconds) === Some(100000 * 2))
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("should merge empty observables") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(2)
        Observable.range(0, 10000)
          .doOnComplete(completed.countDown())
          .mergeMap(_ => Observable.empty[Int])
          .doOnComplete(completed.countDown())
          .subscribe()

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      }
    }

    it("should merge one element observables") {
      for (_ <- 0 until 100) {
        val f = Observable.range(0, 10000).mergeMap(x => Observable.unit(x)).sum.asFuture
        assert(Await.result(f, 10.seconds) === Some((0 until 10000).sum))
      }
    }

    it("should emit a single error downstream and a single cancel upstream, test 1") {
      val legit = Observable.range(0, 10000).map(x => Observable.unit(x))
      val errors = Observable.range(0, 1000).map(_ => Observable.error(new RuntimeException("dummy")))
      val completed = new CountDownLatch(2)
      var sum = 0

      (legit ++ errors)
        .doOnComplete(if (completed.getCount > 0) completed.countDown() else throw new IllegalStateException("completed more than once"))
        .merge.unsafeSubscribe(
          new Observer[Int] {
            def onNext(elem: Int) = {
              sum += elem
              Continue
            }

            def onError(ex: Throwable) = {
              assert(ex.getMessage === "dummy")
              if (completed.getCount > 0)
                completed.countDown()
              else
                throw new IllegalStateException(ex)
            }

            def onComplete() = {
              throw new IllegalStateException("onComplete should never happen")
            }
          })

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum > 0, s"calculated sum $sum is not greater than 0")
    }

    it("should emit a single error downstream and a single cancel upstream, test 2") {
      val errors = Observable.range(0, 1000).map(_ => Observable.error(new RuntimeException("dummy")))
      val completed = new CountDownLatch(2)

      errors.doOnComplete(if (completed.getCount > 0) completed.countDown() else throw new IllegalStateException("completed more than once"))
        .merge.unsafeSubscribe(
          new Observer[Int] {
            def onNext(elem: Int) = {
              throw new IllegalStateException("onNext should have never happened")
            }

            def onError(ex: Throwable) = {
              assert(ex.getMessage === "dummy")
              if (completed.getCount > 0)
                completed.countDown()
              else
                throw new IllegalStateException(ex)
            }

            def onComplete() = {
              throw new IllegalStateException("onComplete should never happen")
            }
          })

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("should abort on error") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(2)
        Observable.range(0, Int.MaxValue)
          .doOnComplete(completed.countDown())
          .mergeMap(x => if (x === 7000) throw new RuntimeException() else Observable.unit(x))
          .subscribe(x => Continue, ex => completed.countDown())

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      }
    }

    it("should terminate everything after downstream canceled") {
      val latch = new SpecialLatch(3)
      val enoughStarted = new CountDownLatch(30)

      val f = Observable.from(0 until Int.MaxValue)
        .doOnComplete(latch.countDown())
        .mergeMap { x =>
        Observable.from(0 until Int.MaxValue)
          .doOnStart { _ => latch.increment(); enoughStarted.countDown()}
          .doOnComplete(latch.countDown())
      }
        .doOnComplete(latch.countDown())
        .take(1000000)
        .map(_ => 1)
        .sum
        .doOnComplete(latch.countDown())
        .asFuture

      assert(enoughStarted.await(20, TimeUnit.SECONDS), "enoughStarted.await should have succeeded")
      assert(Await.result(f, 20.seconds) === Some(1000000))
      assert(latch.await(20.seconds), "latch.await should have completed")
    }


    it("should wait on children to complete, after upstream completes") {
      val latch = new SpecialLatch(3)
      val enoughStarted = new CountDownLatch(30)
      val upstreamNotComplete = Atomic(true)
      val childrenNotComplete = Atomic(true)

      val f = Observable.from(0 until Int.MaxValue)
        .takeWhile(upstreamNotComplete)
        .doOnComplete(latch.countDown())
        .mergeMap { x =>
        Observable.from(0 until Int.MaxValue)
          .doOnStart { _ => latch.increment(); enoughStarted.countDown()}
          .doOnComplete(latch.countDown())
          .takeWhile(childrenNotComplete)
      }
        .doOnComplete(latch.countDown())
        .map(_ => 1)
        .sum
        .doOnComplete(latch.countDown())
        .asFuture

      assert(enoughStarted.await(20, TimeUnit.SECONDS), "enoughStarted.await should have succeeded")
      upstreamNotComplete set false
      assert(!latch.await(1.second), "latch.await should have failed")

      childrenNotComplete set false
      assert(Await.result(f, 20.seconds).get > 0, "no events processed")
      assert(latch.await(20.seconds), "latch.await should have completed")
    }
}

  describe("Observable.merge(Unbounded)") {
    it("should work") {
      for (_ <- 0 until 10000) {
        val result = Observable.from(0 until 100).filter(_ % 5 == 0)
          .map(x => Observable.from(x until (x + 5)))
          .unsafeMerge
          .foldLeft(0)(_ + _).asFuture

        assert(Await.result(result, 4.seconds) === Some((0 until 100).sum))
      }
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      for (_ <- 0 until 1000) {
        val obs = Observable.create[Int] { subscriber =>
          throw new RuntimeException("Test exception")
        }

        val latch = new CountDownLatch(1)
        @volatile var result = ""

        obs.map(x => Observable.unit(x)).unsafeMerge.subscribe(
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

        assert(latch.await(10, TimeUnit.SECONDS), "Latch await failed")
        assert(result === "Test exception")
      }
    }

    it("should protect calls to user code (guideline 6.4)") {
      for (_ <- 0 until 1000) {
        val obs = Observable.from(0 until 100).map { x =>
          if (x < 50) Observable.unit(x) else throw new RuntimeException("test")
        }.unsafeMerge

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
      }
    }

    it("should generate elements, without ordering guaranteed") {
      for (_ <- 0 until 1000) {
        val obs = Observable.from(0 until 100).filter(_ % 5 == 0)
          .map(x => Observable.from(x until (x + 5)))
          .unsafeMerge
          .foldLeft(Seq.empty[Int])(_ :+ _)
          .map(_.sorted)
          .asFuture

        val result = Await.result(obs, 4.seconds)
        assert(result === Some(0 until 100))
      }
    }

    it("should satisfy source.filter(p) == source.mergeMap(x => if (p(x)) unit(x) else empty), without ordering") {
      for (_ <- 0 until 100) {
        val parent = Observable.from(0 until 1000)
        val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
        val res2 = parent.map(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty).unsafeMerge
          .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture

        assert(Await.result(res1, 10.seconds) === Await.result(res2, 4.seconds))
      }
    }

    it("should satisfy source.map(f) == source.map(x => unit(x)).merge(parallelism=1)") {
      for (_ <- 0 until 100) {
        val parent = Observable.from(0 until 1000)
        val res1 = parent.map(_ + 1)
          .foldLeft(Seq.empty[Int])(_ :+ _).asFuture
        val res2 = parent.map(x => Observable.unit(x + 1)).merge(1, Unbounded)
          .foldLeft(Seq.empty[Int])(_ :+ _).asFuture

        assert(Await.result(res1, 10.seconds) === Await.result(res2, 4.seconds))
      }
    }

    it("should satisfy source.filter(f) == source.map(x => if (f(x)) unit(x) else empty).merge(parallelism=1)") {
      for (_ <- 0 until 100) {
        val parent = Observable.from(0 until 1000)
        val res1 = parent.filter(_ % 2 == 0)
          .foldLeft(Seq.empty[Int])(_ :+ _).asFuture
        val res2 = parent.map(x => if (x % 2 == 0) Observable.unit(x) else Observable.empty).merge(1, Unbounded)
          .foldLeft(Seq.empty[Int])(_ :+ _).asFuture

        assert(Await.result(res1, 10.seconds) === Await.result(res2, 4.seconds))
      }
    }

    it("should cancel when downstream has canceled") {
      for (_ <- 0 until 100) {
        val latch = new CountDownLatch(1)
        Observable.from(0 until 1000).doOnComplete(latch.countDown())
          .map(x => Observable.repeat(x)).unsafeMerge.take(1000).subscribe()

        assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
      }
    }

    it("should work with Futures") {
      for (_ <- 0 until 100) {
        val f = Observable.from(0 until 100).map(x => Observable.from(Future(x + 1))).unsafeMerge
          .foldLeft(Seq.empty[Int])(_ :+ _).map(_.sorted).asFuture
        val result = Await.result(f, 4.seconds)
        assert(result === Some(1 to 100))
      }
    }


    it("should work with random stuff") {
      for (repeats <- 0 until 5) {
        val streamLengths = (0 until 5).map(_ => Random.nextInt(1000))
        val completed = new CountDownLatch(2)

        val result = Observable.from(streamLengths)
          .doOnComplete(completed.countDown())
          .map(x => Observable.range(0, x)).unsafeMerge
          .sum
          .doOnComplete(completed.countDown())
          .asFuture

        assert(Await.result(result, 20.seconds) === Some(streamLengths.flatMap(x => 0 until x).sum))
        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      }
    }

    it("should work with never ending streams") {
      val completed = new CountDownLatch(2)
      val result = Observable.repeat(1)
        .doOnComplete(completed.countDown())
        .map(_ => Observable.repeat(2)).unsafeMerge
        .doOnComplete(completed.countDown())
        .take(100).sum.asFuture

      assert(Await.result(result, 20.seconds) === Some(100 * 2))
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("should merge empty observables") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(2)
        Observable.range(0, 10000)
          .doOnComplete(completed.countDown())
          .map(_ => Observable.empty[Int]).unsafeMerge
          .doOnComplete(completed.countDown())
          .subscribe()

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      }
    }

    it("should merge one element observables") {
      for (_ <- 0 until 100) {
        val f = Observable.range(0, 10000).map(x => Observable.unit(x)).unsafeMerge.sum.asFuture
        assert(Await.result(f, 10.seconds) === Some((0 until 10000).sum))
      }
    }

    it("should emit a single error downstream and a single cancel upstream, test 1") {
      val legit = Observable.range(0, 1000).map(x => Observable.unit(x))
      val errors = Observable.range(0, 1000).map(_ => Observable.error(new RuntimeException("dummy")))
      val completed = new CountDownLatch(2)
      var sum = 0

      (legit ++ errors)
        .doOnComplete(if (completed.getCount > 0) completed.countDown() else throw new IllegalStateException("completed more than once"))
        .unsafeMerge.unsafeSubscribe(
          new Observer[Int] {
            def onNext(elem: Int) = {
              sum += elem
              Continue
            }

            def onError(ex: Throwable) = {
              assert(ex.getMessage === "dummy")
              if (completed.getCount > 0)
                completed.countDown()
              else
                throw new IllegalStateException(ex)
            }

            def onComplete() = {
              throw new IllegalStateException("onComplete should never happen")
            }
          })

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum > 0, s"calculated sum $sum is not greater than 0")
    }

    it("should emit a single error downstream and a single cancel upstream, test 2") {
      val errors = Observable.range(0, 1000).map(_ => Observable.error(new RuntimeException("dummy")))
      val completed = new CountDownLatch(2)

      errors.doOnComplete(if (completed.getCount > 0) completed.countDown() else throw new IllegalStateException("completed more than once"))
        .unsafeMerge.unsafeSubscribe(
          new Observer[Int] {
            def onNext(elem: Int) = {
              throw new IllegalStateException("onNext should have never happened")
            }

            def onError(ex: Throwable) = {
              assert(ex.getMessage === "dummy")
              if (completed.getCount > 0)
                completed.countDown()
              else
                throw new IllegalStateException(ex)
            }

            def onComplete() = {
              throw new IllegalStateException("onComplete should never happen")
            }
          })

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("should abort on error") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(2)
        Observable.range(0, Int.MaxValue)
          .doOnComplete(completed.countDown())
          .map(x => if (x === 7000) throw new RuntimeException() else Observable.unit(x))
          .unsafeMerge
          .subscribe(x => Continue, ex => completed.countDown())

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      }
    }

    it("should terminate everything after downstream canceled") {
      val latch = new SpecialLatch(3)

      val f = Observable.from(0 until Int.MaxValue)
        .doOnComplete(latch.countDown())
        .mergeMap { x =>
          Observable.from(0 until Int.MaxValue)
            .doOnStart { _ => latch.increment() }
            .doOnComplete(latch.countDown())
        }
        .doOnComplete(latch.countDown())
        .take(100)
        .map(_ => 1)
        .sum
        .doOnComplete(latch.countDown())
        .asFuture

      assert(Await.result(f, 20.seconds) === Some(100))
      assert(latch.await(20.seconds), "latch.await should have completed")
    }


    it("should wait on children to complete, after upstream completes") {
      val latch = new SpecialLatch(3)
      val enoughStarted = new CountDownLatch(30)
      val upstreamNotComplete = Atomic(true)
      val childrenNotComplete = Atomic(true)

      val f = Observable.from(0 until Int.MaxValue)
        .takeWhile(upstreamNotComplete)
        .doOnComplete(latch.countDown())
        .mergeMap { x =>
          Observable.from(0 until Int.MaxValue)
            .doOnStart { _ => latch.increment(); enoughStarted.countDown()}
            .doOnComplete(latch.countDown())
            .takeWhile(childrenNotComplete)
        }
        .doOnComplete(latch.countDown())
        .map(_ => 1)
        .sum
        .doOnComplete(latch.countDown())
        .asFuture

      assert(enoughStarted.await(10, TimeUnit.SECONDS), "enoughStarted.await should have succeeded")
      upstreamNotComplete set false
      assert(!latch.await(1.second), "latch.await should have failed")

      childrenNotComplete set false
      assert(Await.result(f, 20.seconds).get > 0, "no events processed")
      assert(latch.await(20.seconds), "latch.await should have completed")
    }
  }

  final class SpecialLatch(initialCount: Int) {
    private[this] var latch = new CountDownLatch(initialCount)

    def countDown(): Unit = synchronized {
      latch.countDown()
      notifyAll()
    }

    def increment(): Unit = synchronized {
      if (latch.getCount == 0)
        throw new IllegalStateException("Cannot increment SpecialLatch if already finished")
      else {
        latch = new CountDownLatch(latch.getCount.toInt + 1)
        notifyAll()
      }
    }

    def await(duration: FiniteDuration): Boolean = synchronized {
      val startAt = System.currentTimeMillis()
      val endsAt = startAt + duration.toMillis

      while (latch.getCount > 0 && System.currentTimeMillis() < endsAt) {
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining > 0) wait(remaining)
      }

      latch.getCount == 0
    }
  }
}
