package monifu.reactive

import java.util.concurrent.{CountDownLatch, TimeUnit}
import monifu.reactive.Ack.Continue
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering}
import monifu.reactive.channels.PublishChannel
import monifu.reactive.subjects.PublishSubject
import monifu.concurrent.Implicits.scheduler
import org.scalatest.FunSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}


class DelayTest extends FunSpec {
  describe("Observable.delay(timespan)") {
    it("should work") {
      val now = System.currentTimeMillis()
      val f = Observable.repeat(1).take(100000).delay(200.millis).take(5).reduce(_ + _).asFuture
      val r = Await.result(f, 5.seconds)
      assert(r === Some(5))
      val delayed = System.currentTimeMillis() - now
      assert(delayed >= 200, s"$delayed millis > 200 millis")
    }

    it("should stream onError immediately") {
      val f = Observable.error(new RuntimeException("DUMMY")).delay(10.seconds).asFuture
      Await.ready(f, 2.seconds)
      assert(f.value.get.failed.get.getMessage === "DUMMY")
    }

    it("should be relative to the first event being emitted") {
      val channel = PublishChannel[Int]()
      val f = channel.delay(200.millis).asFuture

      scheduler.scheduleOnce(200.millis, {
        channel.pushNext(1)
      })

      val startTS = System.currentTimeMillis()
      val r = Await.result(f, 5.seconds)
      assert(r === Some(1))

      val endTS = System.currentTimeMillis()
      assert(endTS - startTS >= 400, s"Elapsed ${endTS - startTS} millis >= 400 millis")
    }

    it("should be relative to onComplete if observable is empty") {
      val channel = PublishChannel[Int]()
      val f = channel.delay(200.millis).asFuture

      scheduler.scheduleOnce(200.millis, {
        channel.pushComplete()
      })

      val startTS = System.currentTimeMillis()
      val r = Await.result(f, 5.seconds)
      assert(r === None)

      val endTS = System.currentTimeMillis()
      assert(endTS - startTS >= 400, s"Elapsed ${endTS - startTS} millis >= 400 millis")
    }
  }

  describe("Observable.delay(future)") {
    it("should delay until the future completes with success") {
      val trigger = Promise[Unit]()
      val obs = Observable.unit(1).delay(trigger.future)
      val f = obs.asFuture
      assert(f.value === None)

      trigger.success(())
      val r = Await.result(f, 5.seconds)
      assert(r === Some(1))
    }

    it("should interrupt when the future terminates in error") {
      val trigger = Promise[Unit]()
      val obs = Observable.unit(1).delay(trigger.future)
      val f = obs.asFuture
      assert(f.value === None)

      trigger.failure(new RuntimeException("DUMMY"))
      Await.ready(f, 5.seconds)
      assert(f.value.get.failed.get.getMessage === "DUMMY")
    }

    it("should fail with a buffer overflow in case the policy is OverflowTriggering") {
      val trigger = Promise[Unit]()
      val obs = Observable.repeat(1).delay(OverflowTriggering(1000), trigger.future)
      val f = obs.asFuture
      Await.ready(f, 5.seconds)
      assert(f.value.get.failed.get.isInstanceOf[BufferOverflowException],
        "Should get a BufferOverflowException")
    }

    it("should do back-pressure when the policy is BackPressured") {
      val trigger = Promise[Unit]()
      val subject = PublishSubject[Int]()
      val f = subject.delay(BackPressured(1000), trigger.future)
        .reduce(_ + _).asFuture

      var ack = subject.onNext(1)
      var buffered = 0

      while (ack.isCompleted) {
        assert(ack.value.get === Continue.IsSuccess)
        buffered += 1
        ack = subject.onNext(1)
      }

      assert(buffered === 1000)

      trigger.success(())
      ack.onComplete(_ => subject.onComplete())

      val r = Await.result(f, 5.seconds)
      assert(r === Some(1001))
    }

    it("should trigger error immediately when the policy is BackPressured") {
      val trigger = Promise[Unit]()
      val subject = PublishSubject[Int]()
      val completed = new CountDownLatch(1)
      var triggeredError = null : Throwable
      var sum = 0

      subject.delay(BackPressured(1000), trigger.future)
        .subscribe(
          elem => { sum += elem; Continue },
          error => { triggeredError = error; completed.countDown() },
          () => completed.countDown()
        )

      var ack = Continue : Future[Ack]
      for (_ <- 0 until 1000) {
        ack = subject.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      subject.onNext(1)
      trigger.failure(new RuntimeException("DUMMY"))
      ack.onComplete(_ => subject.onComplete())
      assert(completed.await(5, TimeUnit.SECONDS), "completed.await")

      assert(sum === 0)
      assert(triggeredError.getMessage === "DUMMY")
    }

    it("should trigger error immediately when the policy is OverflowTriggering") {
      val trigger = Promise[Unit]()
      val subject = PublishSubject[Int]()
      val completed = new CountDownLatch(1)
      var triggeredError = null : Throwable
      var sum = 0

      subject.delay(OverflowTriggering(1000), trigger.future)
        .subscribe(
          elem => { sum += elem; Continue },
          error => { triggeredError = error; completed.countDown() },
          () => completed.countDown()
        )

      var ack = Continue : Future[Ack]
      for (_ <- 0 until 1000) {
        ack = subject.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.failure(new RuntimeException("DUMMY"))
      ack.onComplete(_ => subject.onComplete())
      assert(completed.await(5, TimeUnit.SECONDS), "completed.await")

      assert(sum === 0)
      assert(triggeredError.getMessage === "DUMMY")
    }
  }
}
