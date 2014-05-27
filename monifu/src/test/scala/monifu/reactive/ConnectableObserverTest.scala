package monifu.reactive

import org.scalatest.FunSpec
import monifu.reactive.api.{Ack, ConnectableObserver}
import scala.concurrent.Future
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.concurrent.Scheduler.Implicits.global
import java.util.concurrent.{TimeUnit, CountDownLatch}

class ConnectableObserverTest extends FunSpec {
  describe("ConnectableObserver") {
    it("should work when connecting before the streaming started") {
      val latch = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          latch.countDown()
        }
      })

      obs.scheduleFirst(1, 2)
      obs.connect()

      Observable.range(0, 1000).subscribe(obs)

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(sum === (0 until 1000).sum + 3)
    }

    it("should do back-pressure until connected, but still buffer incoming") {
      val completed = new CountDownLatch(1)
      val ackLatch = new CountDownLatch(3)
      @volatile var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      obs.onNext(1).onComplete(_ => ackLatch.countDown())
      obs.onNext(2).onComplete(_ => ackLatch.countDown())
      obs.onNext(3).onComplete(_ => ackLatch.countDown())

      assert(!ackLatch.await(300, TimeUnit.MILLISECONDS), "ackLatch.await should not succeed")
      assert(sum === 0)

      obs.connect()
      assert(ackLatch.await(10, TimeUnit.SECONDS), "ackLatch.await should succeed")

      obs.onComplete()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should succeed")
      assert(sum === 6)
    }

    it("should work when connecting after the streaming started") {
      val streamStarted = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      obs.scheduleFirst(1, 2)
      Observable.range(0, 1000).doWork(_ => streamStarted.countDown()).subscribe(obs)

      assert(streamStarted.await(10, TimeUnit.SECONDS), "streamCompleted.await should have succeeded")
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 1000).sum + 3)
    }

    it("should scheduleFirst and scheduleCompleted") {
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        var count = 0

        def onNext(elem: Int): Future[Ack] = {
          count += 1
          assert(elem === count)
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      obs.scheduleFirst(1, 2, 3)
      obs.scheduleComplete()
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === 6)
    }

    it("should scheduleFirst and scheduleError") {
      val completed = new CountDownLatch(1)
      var error = null : Throwable
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        var count = 0

        def onNext(elem: Int): Future[Ack] = {
          count += 1
          assert(elem === count)
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          error = ex
          assert(completed.getCount === 1)
          completed.countDown()
        }
        def onComplete(): Unit = ()
      })

      obs.scheduleFirst(1, 2, 3)
      obs.schedulerError(new RuntimeException("dummy"))
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === 6)
      assert(error.getMessage === "dummy")
    }

    it("scheduleCompleted should happen before the stream ends") {
      val streamCompleted = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        var count = 0

        def onNext(elem: Int): Future[Ack] = {
          count += 1
          assert(count >= 4 || elem === count)
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      Observable.range(0, 1000).doWork(_ => streamCompleted.countDown()).subscribe(obs)
      assert(streamCompleted.await(10, TimeUnit.SECONDS), "streamCompleted.await should have succeeded")

      obs.scheduleFirst(1, 2, 3)
      obs.scheduleComplete()
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === 6)
    }

    it("should stop after scheduleCompleted and connect") {
      val streamCompleted = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        var count = 0

        def onNext(elem: Int): Future[Ack] = {
          count += 1
          assert(elem === count)
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      obs.scheduleFirst(1, 2, 3)
      obs.scheduleComplete()
      obs.connect()

      Observable.range(0, 1000).doOnComplete(streamCompleted.countDown()).subscribe(obs)
      assert(streamCompleted.await(10, TimeUnit.SECONDS), "streamCompleted.await should have succeeded")

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === 6)
    }

    it("should handle the stress, test 1") {
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          assert(sum === (0 until 100000).sum + (0 until 1000).sum)
          completed.countDown()
        }
      })

      Observable.range(0, 100000).observeOn(global).subscribe(obs)
      obs.scheduleFirst(0 until 1000 : _*)
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 100000).sum + (0 until 1000).sum)
    }

    it("should handle the stress, test 2") {
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          assert(sum === (0 until 100000).sum + (0 until 1000).sum)
          completed.countDown()
        }
      })

      obs.scheduleFirst(0 until 1000 : _*)
      Observable.range(0, 100000).observeOn(global).subscribe(obs)
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 100000).sum + (0 until 1000).sum)
    }

    it("should handle the stress, test 3") {
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }
        def onComplete(): Unit = {
          assert(sum === (0 until 100000).sum + (0 until 1000).sum)
          completed.countDown()
        }
      })

      obs.scheduleFirst(0 until 1000 : _*)
      obs.connect()
      Observable.range(0, 100000).observeOn(global).subscribe(obs)

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 100000).sum + (0 until 1000).sum)
    }

    it("should handle onNext==Done after draining the queue") {
      val streamStarted = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          if (elem < 10) {
            sum += elem
            Continue
          }
          else {
            assert(completed.getCount === 1)
            completed.countDown()
            Done
          }
        }
        def onError(ex: Throwable): Unit = {
          throw new IllegalStateException(s"onError($ex)")
        }
        def onComplete(): Unit = {
          throw new IllegalStateException("onComplete")
        }
      })

      obs.scheduleFirst(1, 2)
      Observable.range(0, 1000).doWork(_ => streamStarted.countDown()).subscribe(obs)
      assert(streamStarted.await(10, TimeUnit.SECONDS), "streamStarted.await should have succeeded")

      obs.connect()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 10).sum + 3)
    }

    it("should handle onNext==Done in connect()") {
      val streamStarted = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          if (elem < 10) {
            sum += elem
            Continue
          }
          else {
            assert(completed.getCount === 1)
            completed.countDown()
            Done
          }
        }
        def onError(ex: Throwable): Unit = {
          throw new IllegalStateException(s"onError($ex)")
        }
        def onComplete(): Unit = {
          throw new IllegalStateException("onComplete")
        }
      })

      obs.scheduleFirst(0 until 20 : _*)
      Observable.range(0, 1000).doWork(_ => streamStarted.countDown()).subscribe(obs)
      assert(streamStarted.await(10, TimeUnit.SECONDS), "streamStarted.await should have succeeded")

      obs.connect()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 10).sum)
    }

    it("stress test 3") {
      val completed = new CountDownLatch(1)
      var sum = 0

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable): Unit = {
          throw new IllegalStateException(s"onError($ex)")
        }
        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      val publish = Observable.range(3, 100000).publish()

      obs.scheduleFirst(1, 2)
      publish.subscribe(obs)

      publish.connect()
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 100000).sum)
    }

    it("it should emit elements in order, test 1") {
      val completed = new CountDownLatch(1)

      val obs = new ConnectableObserver[Int](new Observer[Int] {
        var expecting = 0

        def onNext(elem: Int): Future[Ack] = {
          assert(expecting === elem)
          expecting += 1
          Continue
        }
        def onError(ex: Throwable): Unit = {
          throw new IllegalStateException(s"onError($ex)")
        }
        def onComplete(): Unit = {
          assert(expecting === 100000)
          completed.countDown()
        }
      })

      val publish = Observable.range(1000, 100000).observeOn(global).publish()

      obs.scheduleFirst(0 until 1000 : _*)
      publish.subscribe(obs)

      publish.connect()
      obs.connect()

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("it should emit elements in order, test 2") {
      val completed = new CountDownLatch(1)
      val obs = new ConnectableObserver[Int](new Observer[Int] {
        var expecting = 0

        def onNext(elem: Int): Future[Ack] = {
          assert(expecting === elem)
          expecting += 1
          Continue
        }
        def onError(ex: Throwable): Unit = {
          throw new IllegalStateException(s"onError($ex)")
        }
        def onComplete(): Unit = {
          assert(expecting === 100000)
          completed.countDown()
        }
      })

      obs.scheduleFirst(0 until 1000 : _*)
      obs.connect()

      Observable.range(1000, 100000).observeOn(global).subscribe(obs)
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
    }
  }
}
