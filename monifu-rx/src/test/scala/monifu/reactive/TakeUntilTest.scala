package monifu.reactive

import java.util.concurrent.{TimeUnit, CountDownLatch}

import monifu.reactive.Ack.Continue
import monifu.reactive.channels.PublishChannel
import monifu.reactive.subjects.PublishSubject
import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import concurrent.duration._
import scala.concurrent.Await

class TakeUntilTest extends FunSpec {
  describe("Observable.takeUntil(other: Observable)") {
    it("should emit everything in case the other observable does not emit anything") {
      val other = Observable.never
      val f = Observable.from(0 until 1000)
        .delay(200.millis) // introducing artificial delay
        .takeUntil(other)
        .reduce(_ + _)
        .asFuture

      val r = Await.result(f, 5.seconds)
      assert(r === Some(500 * 999))
    }

    it("should stop in case the other observable signals onNext") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      val completed = new CountDownLatch(1)
      var sum = 0

      channel.takeUntil(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => throw ex,
        () => completed.countDown()
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.pushNext(())
      assert(completed.await(5, TimeUnit.SECONDS), "completed.await")
      assert(sum === 1000)
    }

    it("should stop in case the other observable signals onComplete") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      val completed = new CountDownLatch(1)
      var sum = 0

      channel.takeUntil(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => throw ex,
        () => completed.countDown()
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.pushComplete()
      assert(completed.await(5, TimeUnit.SECONDS), "completed.await")
      assert(sum === 1000)
    }

    it("should stop with error in case the other observable signals onError") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      val errorThrown = new CountDownLatch(1)
      var sum = 0

      channel.takeUntil(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => { errorThrown.countDown() }
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.pushError(new RuntimeException("DUMMY"))
      assert(errorThrown.await(5, TimeUnit.SECONDS), "completed.await")
      assert(sum === 1000)
    }
  }
}
