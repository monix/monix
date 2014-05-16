package monifu.rx

import org.scalatest.FunSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds
import scala.concurrent.Await
import concurrent.duration._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.rx
import monifu.rx.api.{ObservableLike, ObservableTypeClass}


class GenericObservableOperatorsTest[Observable[+T] <: ObservableLike[T, Observable]](builder: ObservableTypeClass[Observable])
  extends FunSpec {

  describe("Observable.map") {
    it("should work") {
      val f = builder.fromTraversable(0 until 100).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(f, 1.second) === Some(1 until 101))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = builder.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.map(x => x).subscribeUnit(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = builder.fromTraversable(0 until 10000).map { x =>
        if (x < 5) x + 1 else throw new RuntimeException("test")
      }

      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribeUnit(
        nextFn = _ => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(errorThrow.getMessage === "test")
    }
  }

  describe("Observable.filter") {
    it("should work") {
      val obs = builder.fromTraversable(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      assert(Await.result(obs, 1.second) === Some((1 to 10).filter(_ % 2 == 0).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = builder.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.filter(_ % 2 == 0).subscribeUnit(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = builder.fromTraversable(0 until 100).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      @volatile var sum = 0
      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribeUnit(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(errorThrow.getMessage === "test")
      assert(sum === (0 until 5).sum)
    }
  }

  describe("Observable.flatMap") {
    it("should work") {
      val result = builder.fromTraversable(0 until 100).filter(_ % 5 == 0)
        .flatMap(x => builder.fromTraversable(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      assert(Await.result(result, 1.second) === Some((0 until 100).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = builder.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.flatMap(x => builder.unit(x)).subscribeUnit(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = builder.fromTraversable(0 until 100).flatMap { x =>
        if (x < 50) builder.unit(x) else throw new RuntimeException("test")
      }

      @volatile var sum = 0
      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribeUnit(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
        }
      )

      latch.await(1, TimeUnit.SECONDS)
      assert(errorThrow.getMessage === "test")
      assert(sum === (0 until 50).sum)
    }

    it("should generate elements in order") {
      val obs = builder.fromTraversable(0 until 100).filter(_ % 5 == 0)
        .flatMap(x => builder.fromTraversable(x until (x + 5)))
        .foldLeft(Seq.empty[Int])(_ :+ _)
        .asFuture

      val result = Await.result(obs, 1.second)
      assert(result === Some(0 until 100))
    }

    it("should satisfy source.filter(p) == source.flatMap(x => if (p(x)) unit(x) else empty)") {
      val parent = builder.fromTraversable(0 until 1000)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => if (x % 5 == 0) builder.unit(x) else builder.empty).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }

    it("should satisfy source.map(f) == source.flatMap(x => unit(x))") {
      val parent = builder.fromTraversable(0 until 1000)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => builder.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }

    it("should satisfy source.map(f).flatten == source.flatMap(f)") {
      val parent = builder.fromTraversable(0 until 1000).filter(_ % 2 == 0)
      val res1 = parent.map(x => builder.fromTraversable(x until (x + 2))).flatten.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => builder.fromTraversable(x until (x + 2))).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 1.second) === Await.result(res2, 1.second))
    }
  }

  describe("Observable.fromTraversable") {
    it("should work without overflow") {
      val n = 1000000L
      val sum = n * (n + 1) / 2
      val obs = builder.fromTraversable(1 to n.toInt)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 20.seconds)
      assert(result === Some(sum))
    }

    it("should stop if terminated with a stop") {
      val n = 1000000L
      val sum = 101 * 50
      val obs = builder.fromTraversable(1 to n.toInt).take(100)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 1.second)
      assert(result === Some(sum))
    }
  }

  describe("Observable.zip") {
    it("should work") {
      val obs1 = builder.fromTraversable(0 until 10).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = builder.fromTraversable(0 until 10).map(_ * 2).map(_.toLong)

      val zipped = obs1.zip(obs2)

      val finalObs = zipped.foldLeft(Seq.empty[(Long,Long)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 1.second)

      assert(result === Some(0.until(10,2).map(x => (x,x))))
    }

    it("should work in four") {
      val obs1 = builder.fromTraversable(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = builder.fromTraversable(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = builder.fromTraversable(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = builder.fromTraversable(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 1.second)

      assert(result === Some(0.until(20,2).map(x => (x,x,x,x))))
    }

    it("should work when length is equal") {
      val obs1 = builder.fromTraversable(0 until 100)
      val obs2 = builder.fromTraversable(0 until 100)
      val zipped = obs1.zip(obs2)

      val finalObs = zipped.foldLeft(Seq.empty[(Int, Int)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 1.second)

      assert(result === Some((0 until 100).map(x => (x,x))))
    }
  }
}

class SyncObservableOperatorsTest
  extends monifu.rx.GenericObservableOperatorsTest[Observable](rx.Observable.Builder)

class AsyncObservableOperatorsTest
  extends monifu.rx.GenericObservableOperatorsTest[AsyncObservable](rx.AsyncObservable.Builder)
