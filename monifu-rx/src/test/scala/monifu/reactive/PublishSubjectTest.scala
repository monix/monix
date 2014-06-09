package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.subjects.PublishSubject
import scala.concurrent.{Future, Await}
import concurrent.duration._
import monifu.concurrent.atomic.padded.Atomic
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.reactive.channels.PublishChannel
import monifu.reactive.observers.ConcurrentObserver


class PublishSubjectTest extends FunSpec {
  describe("PublishSubject") {
    it("should work over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishChannel[Int]()
      val latch = new CountDownLatch(2)

      subject.observeOn(global).filter(x => x % 2 == 0).flatMap(x => Observable.from(x to x + 1))
        .foldLeft(0)(_ + _).foreach { x => result1.set(x); latch.countDown() }
      subject.observeOn(global).filter(x => x % 2 == 0).flatMap(x => Observable.from(x to x + 1))
        .foldLeft(0)(_ + _).foreach { x => result2.set(x); latch.countDown() }

      for (i <- 0 until 10000) subject.pushNext(i)
      subject.pushComplete()

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get === (0 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
      assert(result2.get === result1.get)
    }

    it("should work without asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()
      val latch = new CountDownLatch(2)

      subject.filter(_ % 2 == 0).map(_ + 1).doOnComplete(latch.countDown()).foreach(x => result1.increment(x))
      for (i <- 0 until 20) subject.onNext(i)
      subject.filter(_ % 2 == 0).map(_ + 1).doOnComplete(latch.countDown()).foreach(x => result2.increment(x))
      for (i <- 20 until 10000) subject.onNext(i)

      subject.onComplete()
      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(result1.get === (0 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
      assert(result2.get === (20 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
    }

    it("onError should be emitted over asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = PublishSubject[Int]()
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

      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      var wasCompleted = null : Throwable
      subject.subscribe(_ => Continue, (err) => { wasCompleted = err; Cancel }, () => ())
      assert(wasCompleted != null && wasCompleted.getMessage == "dummy")
    }

    it("should emit onError to new subscribers after it terminated in error") {
      val subject = PublishSubject[Int]()
      subject.onError(new RuntimeException("dummy"))

      var wasCompleted = null : Throwable
      subject.subscribe(_ => Continue, (err) => { wasCompleted = err; Cancel }, () => ())
      assert(wasCompleted != null && wasCompleted.getMessage == "dummy")
    }

    it("onComplete should be emitted over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()
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

      subject.onNext(1)
      subject.onComplete()

      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(result1.get === 1)
      assert(result2.get === 2)

      @volatile var wasCompleted = false
      subject.subscribe(_ => Continue, _ => Cancel, () => { wasCompleted = true; Cancel })
      assert(wasCompleted === true)
    }

    it("should remove subscribers that triggered errors") {
      val received = Atomic(0)
      val errors = Atomic(0)

      val subject = PublishSubject[Int]()
      val latch = new CountDownLatch(2)

      subject.map(x => if (x < 5) x else throw new RuntimeException())
        .subscribe(
          (elem) => { received.increment(elem); Continue },
          (ex) => { errors.increment(); latch.countDown() }
        )

      subject.map(x => x).subscribe(
        x => { received.increment(x); Continue },
        ex => { errors.increment(); latch.countDown() },
        () => latch.countDown()
      )

      subject.onNext(1)
      subject.onNext(2)
      subject.onNext(5)
      subject.onNext(10)
      subject.onNext(1)
      subject.onComplete()

      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(errors.get === 1)
      assert(received.get === 2 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should remove subscribers that where done") {
      val received = Atomic(0)
      val completed = Atomic(0)

      val subject = PublishSubject[Int]()
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

      subject.onNext(1)
      Await.result(subject.onNext(2), 3.seconds)
      assert(completed.get === 0)
      Await.result(subject.onNext(5), 3.seconds)
      assert(completed.get === 1)

      subject.onNext(10)
      subject.onNext(1)
      subject.onComplete()

      Await.result(subject.complete.asFuture, 3.seconds)
      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should be true")

      assert(completed.get === 2)
      assert(received.get === 2 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should complete subscribers immediately after subscription if subject has been completed") {
      val latch = new CountDownLatch(1)

      val subject = PublishSubject[Int]()
      subject.onComplete()

      subject.doOnComplete(latch.countDown()).foreach(x => ())
      latch.await(3, TimeUnit.SECONDS)
    }

    it("should complete subscribers immediately after subscription if subject has been err`d") {
      val latch = new CountDownLatch(1)

      val subject = PublishSubject[Int]()
      subject.onError(null)

      subject.doOnComplete(latch.countDown()).foreach(x => ())
      latch.await(3, TimeUnit.SECONDS)
    }

    it("should protect against synchronous exceptions") {
      class DummyException extends RuntimeException("test")
      val subject = PublishSubject[Int]()

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = {
          onErrorReceived.increment()
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

      subject.onNext(1)
      subject.onNext(10)
      subject.onNext(11)
      Await.result(subject.onNext(12), 3.seconds)

      assert(onNextReceived.get === 3)
      assert(onErrorReceived.get === 2)
    }

    it("should protect against asynchronous exceptions") {
      class DummyException extends RuntimeException("test")
      val subject = PublishSubject[Int]()
      val channel = ConcurrentObserver(subject)

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
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

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
          Cancel
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

      Await.result(channel.onNext(12), 5.seconds)

      assert(onNextReceived.get === 3)
      assert(onErrorReceived.get === 2)
    }
  }
}
