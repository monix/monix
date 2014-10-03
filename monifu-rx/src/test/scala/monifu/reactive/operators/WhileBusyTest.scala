package monifu.reactive.operators

import java.util.concurrent.{TimeUnit, CountDownLatch}

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer}
import monifu.reactive.channels.PublishChannel
import org.scalatest.FunSpec
import concurrent.duration._
import monifu.concurrent.Implicits.globalScheduler
import scala.concurrent.{Await, Promise}

class WhileBusyTest extends FunSpec {
  describe("Observable.whileBusyDrop") {
    it("should work") {
      var dropped = 0
      var received = 0

      val ch = PublishChannel[Int]()
      val barrierOne = new CountDownLatch(2)
      val completed = new CountDownLatch(1)

      val p = Promise[Ack]()
      val future = p.future

      ch.whileBusyDrop(x => { dropped += x; barrierOne.countDown() })
        .subscribe(new Observer[Int] {
        def onNext(elem: Int) = {
          received += elem
          barrierOne.countDown()
          future
        }

        def onError(ex: Throwable) = ()
        def onComplete() = completed.countDown()
      })

      ch.pushNext(10)
      ch.pushNext(20)
      ch.pushNext(30)

      assert(barrierOne.await(5, TimeUnit.SECONDS), "barrierOne.await should succeed")

      assert(dropped === 50)
      assert(received === 10)

      p.success(Continue)
      Await.result(future, 5.seconds)

      ch.pushNext(40, 50)
      ch.pushComplete()

      assert(completed.await(5, TimeUnit.SECONDS), "completed.await should succeed")
      assert(dropped === 50)
      assert(received === 100)
    }
  }
}
