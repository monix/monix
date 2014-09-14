package monifu.reactive

import org.scalatest.FunSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise, TimeoutException}
import monifu.concurrent.Implicits.scheduler


class DelaySubscriptionTest extends FunSpec {
  describe("Observable.delaySubscription(timespan)") {
    it("should work") {
      val now = System.currentTimeMillis()
      val f = Observable.repeat(1).take(100000)
        .delaySubscription(200.millis)
        .reduce(_ + _).asFuture

      val r = Await.result(f, 5.seconds)
      assert(r === Some(100000))
      val delayed = System.currentTimeMillis() - now
      assert(delayed >= 200, s"$delayed millis > 200 millis")
    }
  }

  describe("Observable.delaySubscription(future)") {
    it("should work") {
      val trigger = Promise[Unit]()

      val f = Observable.repeat(1).take(100000)
        .delaySubscription(trigger.future)
        .reduce(_ + _).asFuture

      intercept[TimeoutException] {
        Await.result(f, 200.millis)
      }

      trigger.success(())
      val r = Await.result(f, 5.seconds)
      assert(r === Some(100000))
    }

    it("should trigger error") {
      val trigger = Promise[Unit]()

      val f = Observable.repeat(1).take(100000)
        .delaySubscription(trigger.future)
        .reduce(_ + _).asFuture

      intercept[TimeoutException] {
        Await.result(f, 200.millis)
      }

      class DummyException extends RuntimeException
      trigger.failure(new DummyException)
      intercept[DummyException] {
        Await.result(f, 5.seconds)
      }
    }
  }
}
