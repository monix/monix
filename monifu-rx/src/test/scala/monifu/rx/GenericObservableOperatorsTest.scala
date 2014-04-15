package monifu.rx

import org.scalatest.FunSpec
import scala.concurrent.ExecutionContext.Implicits.global
import monifu.rx.base.{ObservableGen, ObservableBuilder}
import scala.language.higherKinds
import scala.concurrent.Await
import concurrent.duration._
import java.util.concurrent.{TimeUnit, CountDownLatch}

class GenericObservableOperatorsTest[Observable[+T] <: ObservableGen[T]](builder: ObservableBuilder[Observable])
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
}
