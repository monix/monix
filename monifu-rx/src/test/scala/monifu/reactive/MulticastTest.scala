package monifu.reactive

import org.scalatest.FunSpec
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.concurrent.Scheduler.Implicits.global

class MulticastTest extends FunSpec {
  describe("Observable.multicast") {
    it("should not subscribe observers until connect() happens") {
      val latch = new CountDownLatch(2)
      val obs = Observable.fromSequence(1 until 100).publish()

      obs.doOnComplete(latch.countDown()).subscribe()
      obs.doOnComplete(latch.countDown()).subscribe()

      assert(!latch.await(100, TimeUnit.MILLISECONDS), "latch.await should have failed")

      obs.connect()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should complete observers after cancel()") {
      val latch = new CountDownLatch(2)
      val obs = Observable.continuous(()).publish()

      obs.doOnComplete(latch.countDown()).subscribe()
      obs.doOnComplete(latch.countDown()).subscribe()
      val sub = obs.connect()

      assert(!latch.await(100, TimeUnit.MILLISECONDS), "latch.await should have failed")

      sub.cancel()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should not connect() again, once canceled") {
      val latch = new CountDownLatch(2)
      val obs = Observable.continuous(()).publish()

      obs.doOnComplete(latch.countDown()).subscribe()
      obs.doOnComplete(latch.countDown()).subscribe()
      val sub = obs.connect()

      assert(!latch.await(100, TimeUnit.MILLISECONDS), "latch.await should have failed")
      sub.cancel()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(obs.connect().isCanceled, "Subscription should be canceled, but isn't")

      val onCompleted = new CountDownLatch(1)
      obs.doOnComplete(onCompleted.countDown()).subscribe()
      assert(onCompleted.await(10, TimeUnit.SECONDS), "onComplete.await should have succeeded")
    }
  }
}
