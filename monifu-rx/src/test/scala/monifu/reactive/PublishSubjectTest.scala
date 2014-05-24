package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.subjects.PublishSubject
import scala.concurrent.{Future, Await}
import concurrent.duration._
import monifu.concurrent.atomic.padded.Atomic
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.reactive.api.Ack


class PublishSubjectTest extends FunSpec {
  describe("PublishSubject") {
    it("should work over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()

      subject.filter(x => x % 2 == 0).flatMap(x => Observable.fromSequence(x to x + 1))
        .foldLeft(0)(_ + _).foreach(x => result1.set(x))
      for (i <- 0 until 100) subject.onNext(i)
      subject.filter(x => x % 2 == 0).flatMap(x => Observable.fromSequence(x to x + 1))
        .foldLeft(0)(_ + _).foreach(x => result2.set(x))
      for (i <- 100 until 10000) subject.onNext(i)

      Await.result(subject.onComplete(), 3.seconds)

      assert(result1.get === (0 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
      assert(result2.get === (100 until 10000).filter(_ % 2 == 0).flatMap(x => x to (x + 1)).sum)
    }

    it("should work without asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()

      subject.filter(_ % 2 == 0).map(_ + 1).foreach(x => result1.increment(x))
      for (i <- 0 until 20) subject.onNext(i)
      subject.filter(_ % 2 == 0).map(_ + 1).foreach(x => result2.increment(x))
      for (i <- 20 until 10000) subject.onNext(i)

      Await.result(subject.onComplete(), 3.seconds)
      assert(result1.get === (0 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
      assert(result2.get === (20 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
    }

    it("onError should be emitted without asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = PublishSubject[Int]()

      subject.subscribe(
        elem => Continue,
        ex => { result1.set(ex); Done }
      )
      subject.subscribe(
        elem => Continue,
        ex => { result2.set(ex); Done }
      )

      subject.onError(new RuntimeException("dummy"))

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      @volatile var wasCompleted = false
      subject.subscribe(_ => Continue, _ => Done, () => { wasCompleted = true; Done })
      assert(wasCompleted === true)
    }

    it("onError should be emitted over asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = PublishSubject[Int]()

      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => { result1.set(ex); Done }
      )
      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => { result2.set(ex); Done }
      )

      subject.onNext(1)
      Await.result(subject.onError(new RuntimeException("dummy")), 1.second)

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      @volatile var wasCompleted = false
      subject.subscribe(_ => Continue, _ => Done, () => { wasCompleted = true; Done })
      assert(wasCompleted === true)
    }

    it("onComplete should be emitted without asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()

      subject.subscribe(
        elem => Continue,
        ex => Done,
        () => { result1.set(1); Done }
      )
      subject.subscribe(
        elem => Continue,
        ex => Done,
        () => { result2.set(2); Done }
      )

      subject.onComplete()

      assert(result1.get === 1)
      assert(result2.get === 2)

      @volatile var wasCompleted = false
      subject.subscribe(_ => Continue, _ => Done, () => { wasCompleted = true; Done })
      assert(wasCompleted === true)
    }

    it("onComplete should be emitted over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()

      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => Done,
        () => { result1.set(1); Done }
      )
      subject.observeOn(global).subscribe(
        elem => Continue,
        ex => Done,
        () => { result2.set(2); Done }
      )

      subject.onNext(1)
      Await.result(subject.onComplete(), 1.second)

      assert(result1.get === 1)
      assert(result2.get === 2)

      @volatile var wasCompleted = false
      subject.subscribe(_ => Continue, _ => Done, () => { wasCompleted = true; Done })
      assert(wasCompleted === true)
    }

    it("should map synchronously") {
      var result = 0
      val subject = PublishSubject[Int]()
      subject.map(x => x + 1).foreach(x => result = x)

      subject.onNext(1)
      assert(result === 2)
      subject.onComplete()
    }

    it("should filter synchronously") {
      var result = 0
      val subject = PublishSubject[Int]()
      subject.filter(x => x % 2 == 0).foreach(x => result = x)

      subject.onNext(2)
      subject.onNext(1)
      assert(result === 2)
      subject.onComplete()
    }

    it("should remove subscribers that triggered errors") {
      val received = Atomic(0)
      val errors = Atomic(0)

      val subject = PublishSubject[Int]()
      subject.map(x => if (x < 5) x else throw new RuntimeException()).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => { errors.increment(); Done }
      )
      subject.map(x => x)
        .foreach(x => received.increment(x))

      subject.onNext(1)
      subject.onNext(2)
      subject.onNext(5)
      subject.onNext(10)
      subject.onNext(1)
      Await.result(subject.onComplete(), 3.seconds)

      assert(errors.get === 1)
      assert(received.get === 2 * 1 + 2 * 2 + 5 + 10 + 1)
    }

    it("should remove subscribers that where done") {
      val received = Atomic(0)
      val completed = Atomic(0)

      val subject = PublishSubject[Int]()
      subject.takeWhile(_ < 5).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => Done,
        () => { completed.increment(); Done }
      )
      subject.map(x => x).subscribe(
        (elem) => { received.increment(elem); Continue },
        (ex) => Done,
        () => { completed.increment(); Done }
      )

      subject.onNext(1)
      Await.result(subject.onNext(2), 3.seconds)
      assert(completed.get === 0)
      Await.result(subject.onNext(5), 3.seconds)
      assert(completed.get === 1)

      subject.onNext(10)
      subject.onNext(1)
      Await.result(subject.onComplete(), 3.seconds)

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

      assert(onNextReceived.get === 3)
      assert(onErrorReceived.get === 2)
    }

    it("should protect against asynchronous exceptions") {
      class DummyException extends RuntimeException("test")
      val subject = PublishSubject[Int]()

      val onNextReceived = Atomic(0)
      val onErrorReceived = Atomic(0)

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
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

      subject.subscribe(new Observer[Int] {
        def onError(ex: Throwable) = Future {
          onErrorReceived.increment()
          Done
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

      Await.result(subject.onNext(12), 5.seconds)

      assert(onNextReceived.get === 3)
      assert(onErrorReceived.get === 2)
    }
  }
}
