package monifu.reactive

import java.util.concurrent.{TimeUnit, CountDownLatch}

import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.reactive.observers.{ConnectableObserver, ConcurrentObserver}
import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import scala.concurrent.Future


class ConnectableObserverTest extends FunSpec {
  describe("ConnectableObserver") {
    it("should work when connecting before the streaming started") {
      for (i <- 0 until 10000) {
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

        obs.pushNext(1, 2)
        obs.connect()

        Observable.range(0, 1000).subscribe(obs)

        assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
        assert(sum === (0 until 1000).sum + 3)
      }
    }

    it("should back-pressure Observable.unit if subscribed before connect") {
      for (i <- 0 until 10000) {
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

        if (i % 2 == 0)
          Observable.unit(3).subscribe(obs)
        else
          Observable.unit(3).observeOn(global).subscribe(obs)

        if (i % 3 != 0) {
          obs.pushNext(1, 2)
          obs.connect()
        }
        else
          global.scheduleOnce {
            obs.pushNext(1, 2)
            obs.connect()
          }

        assert(latch.await(1, TimeUnit.SECONDS), "latch.await should have succeeded")
        assert(sum === 6)
      }
    }

    it("should do back-pressure until connected, but still buffer incoming") {
      for (_ <- 0 until 50) {
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

        val channel = ConcurrentObserver(obs)
        channel.onNext(1).onComplete(_ => ackLatch.countDown())
        channel.onNext(2).onComplete(_ => ackLatch.countDown())
        channel.onNext(3).onComplete(_ => ackLatch.countDown())

        assert(!ackLatch.await(1, TimeUnit.MILLISECONDS), "ackLatch.await should not succeed")
        assert(sum === 0)

        obs.connect()
        assert(ackLatch.await(10, TimeUnit.SECONDS), "ackLatch.await should succeed")

        channel.onComplete()
        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should succeed")
        assert(sum === 6)
      }
    }

    it("should work when connecting after the streaming started") {
      for (_ <- 0 until 1000) {
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

        obs.pushNext(1, 2)
        Observable.range(0, 1000).doWork(_ => streamStarted.countDown()).subscribe(obs)

        assert(streamStarted.await(10, TimeUnit.SECONDS), "streamCompleted.await should have succeeded")
        obs.connect()

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === (0 until 1000).sum + 3)
      }
    }

    it("should scheduleFirst and scheduleCompleted") {
      for (_ <- 0 until 1000) {
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

        obs.pushNext(1, 2, 3)
        obs.pushComplete()
        obs.connect()

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 6)
      }
    }

    it("should scheduleFirst and scheduleError") {
      for (_ <- 0 until 1000) {
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

        obs.pushNext(1, 2, 3)
        obs.pushError(new RuntimeException("dummy"))
        obs.connect()

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 6)
        assert(error.getMessage === "dummy")
      }
    }

    it("scheduleCompleted should happen before the stream ends") {
      for (_ <- 0 until 1000) {
        val streamStarted = new CountDownLatch(1)
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

        Observable.range(0, 1000).doOnStart(_ => streamStarted.countDown()).subscribe(obs)
        assert(streamStarted.await(10, TimeUnit.SECONDS), "streamCompleted.await should have succeeded")

        obs.pushNext(1, 2, 3)
        obs.pushComplete()
        obs.connect()

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 6)
      }
    }

    it("should stop after scheduleCompleted and connect") {
      for (_ <- 0 until 1000) {
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

        obs.pushNext(1, 2, 3)
        obs.pushComplete()
        obs.connect()

        Observable.range(0, 1000).doOnComplete(streamCompleted.countDown()).subscribe(obs)
        assert(streamCompleted.await(10, TimeUnit.SECONDS), "streamCompleted.await should have succeeded")

        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 6)
      }
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

      Observable.from(0 until 100000).observeOn(global).subscribe(obs)
      obs.pushNext(0 until 1000 : _*)
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

      obs.pushNext(0 until 1000 : _*)
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

      obs.pushNext(0 until 1000 : _*)
      obs.connect()

      Observable.range(0, 100000).observeOn(global).subscribe(obs)

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 100000).sum + (0 until 1000).sum)
    }

    it("should handle onNext==Cancel after draining the queue") {
      for (_ <- 0 until 10000) {
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
              Cancel
            }
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(s"onError($ex)")
          }
          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete")
          }
        })

        obs.pushNext(1, 2)
        Observable.range(0, 1000).doWork(_ => streamStarted.countDown()).subscribe(obs)
        assert(streamStarted.await(10, TimeUnit.SECONDS), "streamStarted.await should have succeeded")

        obs.connect()
        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === (0 until 10).sum + 3)
      }
    }

    it("should handle onNext==Cancel in connect()") {
      for (_ <- 0 until 10000) {
        val streamStarted = new CountDownLatch(1)
        val completed = new CountDownLatch(1)
        var sum = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            sum += elem

            if (elem < 10) {
              Continue
            }
            else if (elem == 10) {
              completed.countDown()
              Cancel
            }
            else
              throw new IllegalStateException("Received illegal onNext($elem)")
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(s"onError($ex)")
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete")
          }
        })

        obs.pushNext(0 until 20 : _*)
        Observable.range(100, 1000).doOnStart(_ => streamStarted.countDown()).subscribe(obs)
        assert(streamStarted.await(10, TimeUnit.SECONDS), "streamStarted.await should have succeeded")

        obs.connect()
        assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === (0 to 10).sum)
      }
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

      obs.pushNext(1, 2)
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

      obs.pushNext(0 until 1000 : _*)
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

      obs.pushNext(0 until 1000 : _*)
      obs.connect()

      Observable.range(1000, 100000).observeOn(global).subscribe(obs)
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("should emit single element before connect and single element after connect, test 1") {
      for (_ <- 0 until 10000) {
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

        global.scheduleOnce {
          obs.pushNext(1)
          obs.connect()
        }

        global.scheduleOnce {
          obs.onNext(2)
          obs.onComplete()
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 3)
      }
    }

    it("should emit single element before connect and single element after connect, test 2") {
      for (_ <- 0 until 10000) {
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

        obs.pushNext(1)
        obs.connect()
        obs.onNext(2)
        obs.onComplete()

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 3)
      }
    }

    it("should emit single element before connect and single element after connect, test 3") {
      for (_ <- 0 until 10000) {
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

        obs.pushNext(1)
        obs.onNext(2)
        obs.connect()
        obs.onComplete()

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 3)
      }
    }

    it("should close connection before connect, test 1") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(s"onError($ex) should not happen")
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushComplete()
          obs.connect()
        }

        global.scheduleOnce {
          obs.onError(new RuntimeException("dummy"))
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should close connection before connect, test 2") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(s"onError($ex) should not happen")
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushComplete()
          obs.connect()
        }

        global.scheduleOnce {
          obs.onComplete()
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should close connection before connect, test 3") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(s"onError($ex) should not happen")
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushComplete()
          obs.connect()
        }

        global.scheduleOnce {
          obs.onNext(4)
          obs.onComplete()
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should close connection before connect, test 4") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushError(new RuntimeException("dummy"))
          obs.connect()
        }

        global.scheduleOnce {
          obs.onNext(4)
          obs.onComplete()
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should close connection before connect, test 5") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushError(new RuntimeException("dummy"))
          obs.connect()
        }

        global.scheduleOnce {
          obs.onComplete()
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should close connection before connect, test 6") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushError(new RuntimeException("dummy"))
          obs.connect()
        }

        global.scheduleOnce {
          obs.onError(new RuntimeException("dummy2"))
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should close connection before connect, test 7") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.pushNext(2)
          obs.pushError(new RuntimeException("dummy"))
          obs.connect()
        }

        global.scheduleOnce {
          obs.onNext(1)
          obs.onError(new RuntimeException("dummy2"))
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 3)
      }
    }

    it("should emit single element before connect and single element after connect, followed by error, test 1") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen = elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        obs.pushNext(1)
        obs.onNext(2)
        obs.connect()
        obs.onError(new RuntimeException("dummy"))

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen > 0)
      }
    }

    it("should emit single element before connect and single element after connect, followed by error, test 2") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen = elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        obs.pushNext(1)
        obs.connect()

        obs.onNext(2)
        obs.onError(new RuntimeException("dummy"))

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen > 0)
      }
    }

    it("should emit single element before connect and single element after connect, followed by error, test 3") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen = elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.connect()
        }

        global.scheduleOnce {
          obs.onNext(2)
          obs.onError(new RuntimeException("dummy"))
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen > 0)
      }
    }

    it("should emit single element before connect, followed by error after connect") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var seen = 0

        val obs = new ConnectableObserver[Int](new Observer[Int] {
          def onNext(elem: Int): Future[Ack] = {
            assert(completed.getCount === 1)
            seen = elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            assert(ex.getMessage === "dummy")
            completed.countDown()
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete should not happen")
          }
        })

        global.scheduleOnce {
          obs.pushNext(1)
          obs.connect()
        }

        global.scheduleOnce {
          obs.onError(new RuntimeException("dummy"))
        }

        assert(completed.await(2, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen > 0)
      }
    }
  }
}
