package monifu.reactive

import java.util.concurrent.{CountDownLatch, TimeUnit}

import monifu.concurrent.Scheduler.Implicits.global
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.extensions._
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.reactive.channels.ReplayChannel
import monifu.reactive.observers.ConcurrentObserver
import monifu.reactive.subjects.ReplaySubject
import org.scalatest.FunSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class ReplaySubjectTest extends FunSpec {
  describe("ReplaySubject") {
    it("should work over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)

      val completed = new CountDownLatch(2)
      val barrier = new CountDownLatch(1)

      subject.filter(x => x % 2 == 0)
        .flatMap(x => Observable.from(x to x + 1))
        .doWork(x => if (x == 99) barrier.countDown())
        .foldLeft(0)(_ + _)
        .doOnComplete(completed.countDown())
        .foreach(x => result1.set(x))

      for (i <- 0 until 100) channel.onNext(i)
      assert(barrier.await(10, TimeUnit.SECONDS), "barrier.await should have succeeded")

      subject.filter(x => x % 2 == 0)
        .flatMap(x => Observable.from(x to x + 1))
        .foldLeft(0)(_ + _)
        .doOnComplete(completed.countDown())
        .foreach(x => result2.set(x))

      for (i <- 100 until 10000) channel.onNext(i)

      channel.onComplete()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")

      assert(result1.get === (0 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
      assert(result2.get === (0 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
    }

    it("onError should be emitted over asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)
      val latch = new CountDownLatch(2)

      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => { result1.set(ex); latch.countDown() }
      )
      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => { result2.set(ex); latch.countDown() }
      )

      channel.onNext(1)
      channel.onError(new RuntimeException("dummy"))

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      val wasCompleted = new CountDownLatch(1)
      subject.subscribe(_ => Continue, _ => wasCompleted.countDown(), () => Cancel)
      assert(wasCompleted.await(3, TimeUnit.SECONDS), "wasCompleted.await should have succeeded")
    }

    it("onComplete should be emitted over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)
      val latch = new CountDownLatch(2)

      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => Cancel,
        () => { result1.set(1); latch.countDown() }
      )
      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => Cancel,
        () => { result2.set(2); latch.countDown() }
      )

      channel.onNext(1)
      channel.onComplete()

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get === 1)
      assert(result2.get === 2)

      val completeLatch = new CountDownLatch(1)
      subject.subscribe(_ => Continue, _ => (), () => { completeLatch.countDown() })
      assert(completeLatch.await(10, TimeUnit.SECONDS), "completeLatch.await should have succeeded")
    }

    it("should remove subscribers that triggered errors") {
      val received = Atomic(0)
      val errors = Atomic(0)

      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)
      val latch = new CountDownLatch(1)

      subject.map(x => if (x < 5) x else throw new RuntimeException()).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => { errors.increment(); latch.countDown(); Cancel }
      )
      subject.map(x => x)
        .foreach(x => received.increment(x))

      channel.onNext(1)
      channel.onNext(2)
      channel.onNext(5)
      channel.onNext(10)
      channel.onNext(1)
      channel.onComplete()

      Await.result(subject.complete.asFuture, 10.seconds)
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(errors.get === 1)
      assert(received.get === 2 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should remove subscribers that where done") {
      val received = Atomic(0)
      val completed = Atomic(0)

      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)
      val latch = new CountDownLatch(2)

      subject.takeWhile(_ < 5).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => Cancel,
        () => { completed.increment(); latch.countDown(); Cancel }
      )
      subject.map(x => x).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => Cancel,
        () => { completed.increment(); latch.countDown(); Cancel }
      )

      channel.onNext(1)
      Await.result(channel.onNext(2), 10.seconds)
      assert(completed.get === 0)
      Await.result(channel.onNext(5), 10.seconds)
      assert(completed.get === 1)

      channel.onNext(10)
      channel.onNext(1)
      channel.onComplete()

      Await.result(subject.complete.asFuture, 10.seconds)
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should be true")

      assert(completed.get === 2)
      assert(received.get === 2 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should complete subscribers immediately after subscription if subject has been completed") {
      val latch = new CountDownLatch(1)

      val subject = ReplaySubject[Int]()
      subject.onComplete()

      subject.doOnComplete(latch.countDown()).foreach(x => ())
      latch.await(10, TimeUnit.SECONDS)
    }

    it("should complete subscribers immediately after subscription if subject has been err`d") {
      val latch = new CountDownLatch(1)

      val subject = ReplaySubject[Int]()
      subject.onError(null)

      subject.doOnComplete(latch.countDown()).foreach(x => ())
      latch.await(10, TimeUnit.SECONDS)
    }

    it("should protect against synchronous exceptions in onNext") {
      class DummyException extends RuntimeException("test")
      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)
      val latch = new CountDownLatch(2)

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = {
          onErrorReceived.increment()
          latch.countDown()
          Cancel
        }

        def onComplete() =
          throw new NotImplementedError

        def onNext(elem: Int) = {
          if (elem == 10)
            throw new DummyException()
          onNextReceived.increment()
          Continue
        }
      })

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = {
          onErrorReceived.increment()
          latch.countDown()
          Cancel
        }

        def onComplete() =
          throw new NotImplementedError

        def onNext(elem: Int) = {
          if (elem == 11)
            throw new DummyException()
          onNextReceived.increment()
          Continue
        }
      })

      channel.onNext(1)
      channel.onNext(10)
      channel.onNext(11)
      channel.onNext(12)

      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(onNextReceived.get === 3)
      assert(onErrorReceived.get === 2)
    }

    it("should protect against asynchronous exceptions in onNext") {
      class DummyException extends RuntimeException("test")
      val subject = ReplaySubject[Int]()
      val channel = ConcurrentObserver(subject)

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)
      val latch = new CountDownLatch(3)

      subject.subscribeOn(global).observeOn(global).subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
          latch.countDown()
          Cancel
        }

        def onComplete() =
          throw new NotImplementedError

        def onNext(elem: Int) = Future {
          if (elem == 10)
            throw new DummyException()
          onNextReceived.increment()
          Continue
        }
      })

      subject.observeOn(global).map(x => x).observeOn(global).subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
          latch.countDown()
        }

        def onComplete() =
          throw new NotImplementedError

        def onNext(elem: Int) = Future {
          if (elem == 10)
            throw new DummyException()
          onNextReceived.increment()
          Continue
        }
      })

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
          latch.countDown()
        }

        def onComplete() =
          throw new NotImplementedError

        def onNext(elem: Int) = Future {
          if (elem == 11)
            throw new DummyException()
          onNextReceived.increment()
          Continue
        }
      })

      channel.onNext(1)
      channel.onNext(10)
      channel.onNext(11)
      channel.onNext(12)

      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(onNextReceived.get === 4)
    }

    it("should emit in parallel") {
      val subject = ReplayChannel[Int]()
      val subject1Complete = new CountDownLatch(1)
      val receivedFirst = new CountDownLatch(2)
      val subject2ReceivedSecond = new CountDownLatch(1)

      @volatile var sum1 = 0
      var sum2 = 0

      // lazy subscriber
      subject.doOnComplete(subject1Complete.countDown()).subscribe { x =>
        if (x == 1) {
          sum1 += x
          receivedFirst.countDown()
          Continue
        }
        else if (x == 2)
          Future.delayedResult(1.second) {
            sum1 += x
            Cancel
          }
        else
          throw new IllegalStateException(s"Illegal onNext($x)")
      }

      subject.subscribe { x =>
        sum2 += x
        if (x == 1) receivedFirst.countDown()
        if (x == 2) subject2ReceivedSecond.countDown()
        Continue
      }

      subject.pushNext(1)
      assert(receivedFirst.await(3, TimeUnit.SECONDS), "receivedFirst.await should have succeeded")

      assert(sum1 === 1)
      assert(sum2 === 1)

      subject.pushNext(2)
      assert(subject2ReceivedSecond.await(3, TimeUnit.SECONDS), "subject2ReceivedSecond.await should have succeeded")

      assert(sum1 === 1)
      assert(sum2 === 3)

      subject.pushComplete()
      subject1Complete.await(3, TimeUnit.SECONDS)

      assert(sum1 === 3)
    }

    it("should handle the stress when adding subscribers, test 1") {
      val subject = ReplaySubject[Int]()
      val latch = new CountDownLatch(1000)
      val sum = Atomic(0)

      val obs = Observable.repeat(1,2).take(10000).multicast(subject)
      val cancelable = obs.connect()

      for (_ <- 0 until 1000)
        obs.take(2).subscribe(new Observer[Int] {
          private[this] var received = Vector.empty[Int]
          def onNext(elem: Int) = {
            received = received :+ elem
            sum += elem
            Continue
          }

          def onComplete(): Unit = {
            if (received.sum != 3)
              println(received)
            latch.countDown()
          }

          def onError(ex: Throwable): Unit =
            global.reportFailure(ex)
        })

      try {
        assert(latch.await(30, TimeUnit.SECONDS), "latch.await should have completed")
        assert(sum.get === 3 * 1000)
      }
      finally {
        cancelable.cancel()
      }
    }

    it("should handle the stress when adding subscribers, test 2") {
      val subject = ReplaySubject[Int]()
      val latch = new CountDownLatch(1000)
      val sum = Atomic(0)

      val obs = Observable.repeat(1,2).take(10000).multicast(subject)
      for (_ <- 0 until 1000)
        obs.take(2).subscribe(new Observer[Int] {
          private[this] var received = Vector.empty[Int]
          def onNext(elem: Int) = {
            received = received :+ elem
            sum += elem
            Continue
          }

          def onComplete(): Unit = {
            if (received.sum != 3)
              println(received)
            latch.countDown()
          }

          def onError(ex: Throwable): Unit =
            global.reportFailure(ex)
        })

      val cancelable = obs.connect()
      try {
        assert(latch.await(30, TimeUnit.SECONDS), "latch.await should have completed")
        assert(sum.get === 3 * 1000)
      }
      finally {
        cancelable.cancel()
      }
    }
  }
}
