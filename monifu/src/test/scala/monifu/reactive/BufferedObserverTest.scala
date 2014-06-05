package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import scala.concurrent.Promise
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.Continue
import monifu.reactive.api.{BufferOverflowException, Ack}
import monifu.reactive.observers.BufferedObserver
import monifu.reactive.api.BufferPolicy.OverflowTriggering


class BufferedObserverTest extends FunSpec {
  describe("BufferedObserver(OverflowTriggering)") {
    it("should trigger overflow when over capacity") {
      val errorCaught = new CountDownLatch(1)
      val receivedLatch = new CountDownLatch(5)
      val promise = Promise[Ack]()

      val underlying = new Observer[Int] {
        var received = 0
        def onNext(elem: Int) = {
          received += 1
          if (received < 6) {
            receivedLatch.countDown()
            Continue
          }
          else if (received == 6) {
            receivedLatch.countDown()
            // never ending piece of processing
            promise.future
          }
          else
            Continue
        }

        def onError(ex: Throwable) = {
          assert(ex.isInstanceOf[BufferOverflowException],
            s"Exception $ex is not a buffer overflow error")
          errorCaught.countDown()
        }

        def onComplete() = {
          throw new IllegalStateException("Should not onComplete")
        }
      }

      val buffer = BufferedObserver(underlying, OverflowTriggering(5))

      assert(buffer.onNext(1) === Continue)
      assert(buffer.onNext(2) === Continue)
      assert(buffer.onNext(3) === Continue)
      assert(buffer.onNext(4) === Continue)
      assert(buffer.onNext(5) === Continue)

      assert(receivedLatch.await(10, TimeUnit.SECONDS), "receivedLatch.await should have succeeded")
      assert(!errorCaught.await(2, TimeUnit.SECONDS), "errorCaught.await should have failed")

      buffer.onNext(6)
      for (i <- 0 until 10) buffer.onNext(7)
      assert(!errorCaught.await(1, TimeUnit.MILLISECONDS), "errorCaught.await should have failed")

      promise.success(Continue)
      assert(errorCaught.await(5, TimeUnit.SECONDS), "errorCaught.await should have succeeded")
    }
  }
}
