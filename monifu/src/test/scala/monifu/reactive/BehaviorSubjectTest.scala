package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.subjects.BehaviorSubject
import scala.concurrent.{Future, Await}
import concurrent.duration._
import monifu.concurrent.atomic.padded.Atomic
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.{Done, Continue}


class BehaviorSubjectTest extends FunSpec {
  describe("BehaviorSubject") {
    it("should work over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = BehaviorSubject[Int](10)
      val latch = new CountDownLatch(2)

      subject.filter(x => x % 2 == 0).flatMap(x => Observable.fromSequence(x to x + 1))
        .foldLeft(0)(_ + _).doOnComplete(latch.countDown()).foreach(x => result1.set(x))

      for (i <- 0 until 100) subject.onNext(i)
      subject.filter(x => x % 2 == 0).flatMap(x => Observable.fromSequence(x to x + 1))
        .foldLeft(0)(_ + _).doOnComplete(latch.countDown()).foreach(x => result2.set(x))
      for (i <- 100 until 10000) subject.onNext(i)

      subject.onComplete()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get === 21 + (0 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
      assert(result2.get === (100 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
    }

    it("should work without asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = BehaviorSubject[Int](10)
      val latch = new CountDownLatch(2)

      subject.filter(_ % 2 == 0).map(_ + 1).doOnComplete(latch.countDown()).foreach(x => result1.increment(x))
      for (i <- 0 until 20) subject.onNext(i)
      subject.filter(_ % 2 == 0).map(_ + 1).doOnComplete(latch.countDown()).foreach(x => result2.increment(x))
      for (i <- 20 until 10000) subject.onNext(i)

      subject.onComplete()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get === 11 + (0 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
      assert(result2.get === (20 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
    }

    it("onError should be emitted over asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = BehaviorSubject[Int](10)
      val latch = new CountDownLatch(2)

      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => { result1.set(ex); latch.countDown() }
      )
      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => { result2.set(ex); latch.countDown() }
      )

      subject.onNext(1)
      subject.onError(new RuntimeException("dummy"))

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      @volatile var wasCompleted = false
      subject.subscribe(_ => Continue, _ => {wasCompleted = true; Done}, () => Done)
      assert(wasCompleted === true)
    }

    it("onComplete should be emitted over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = BehaviorSubject[Int](10)
      val latch = new CountDownLatch(2)

      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => Done,
        () => { result1.set(1); latch.countDown() }
      )
      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => Done,
        () => { result2.set(2); latch.countDown() }
      )

      subject.onNext(1)
      subject.onComplete()

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

      val subject = BehaviorSubject[Int](1)
      val latch = new CountDownLatch(1)

      subject.map(x => if (x < 5) x else throw new RuntimeException()).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => { errors.increment(); latch.countDown(); Done }
      )
      subject.map(x => x)
        .foreach(x => received.increment(x))

      subject.onNext(1)
      subject.onNext(2)
      subject.onNext(5)
      subject.onNext(10)
      subject.onNext(1)
      subject.onComplete()

      Await.result(subject.complete.asFuture, 10.seconds)
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(errors.get === 1)
      assert(received.get === 4 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should remove subscribers that where done") {
      val received = Atomic(0)
      val completed = Atomic(0)

      val subject = BehaviorSubject[Int](1)
      val latch = new CountDownLatch(2)

      subject.takeWhile(_ < 5).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => Done,
        () => { completed.increment(); latch.countDown(); Done }
      )
      subject.map(x => x).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => Done,
        () => { completed.increment(); latch.countDown(); Done }
      )

      subject.onNext(1)
      Await.result(subject.onNext(2), 10.seconds)
      assert(completed.get === 0)
      Await.result(subject.onNext(5), 10.seconds)
      assert(completed.get === 1)

      subject.onNext(10)
      subject.onNext(1)
      subject.onComplete()

      Await.result(subject.complete.asFuture, 10.seconds)
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should be true")

      assert(completed.get === 2)
      assert(received.get === 4 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should complete subscribers immediately after subscription if subject has been completed") {
      val latch = new CountDownLatch(1)

      val subject = BehaviorSubject[Int](10)
      subject.onComplete()

      subject.doOnComplete(latch.countDown()).foreach(x => ())
      latch.await(10, TimeUnit.SECONDS)
    }

    it("should complete subscribers immediately after subscription if subject has been err`d") {
      val latch = new CountDownLatch(1)

      val subject = BehaviorSubject[Int](10)
      subject.onError(null)

      subject.doOnComplete(latch.countDown()).foreach(x => ())
      latch.await(10, TimeUnit.SECONDS)
    }

    it("should protect against synchronous exceptions in onNext") {
      class DummyException extends RuntimeException("test")
      val subject = BehaviorSubject[Int](0)

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)
      val latch = new CountDownLatch(2)

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = {
          onErrorReceived.increment()
          latch.countDown()
          Done
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
          Done
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

      subject.onNext(1)
      subject.onNext(10)
      subject.onNext(11)
      subject.onNext(12)

      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(onNextReceived.get === 5)
      assert(onErrorReceived.get === 2)
    }

    it("should protect against asynchronous exceptions in onNext") {
      class DummyException extends RuntimeException("test")
      val subject = BehaviorSubject[Int](0)

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)
      val latch = new CountDownLatch(3)

      subject.subscribeOn(global).observeOn(global).subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
          latch.countDown()
          Done
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

      subject.onNext(1)
      subject.onNext(10)
      subject.onNext(11)
      subject.onNext(12)

      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(onNextReceived.get === 7)
    }
  }
}
