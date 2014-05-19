package monifu.rx

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import monifu.rx.subjects.PublishSubject
import scala.concurrent.Await
import concurrent.duration._
import monifu.concurrent.atomic.padded.Atomic

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

      Await.result(subject.onCompleted(), 3.seconds)

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

      Await.result(subject.onCompleted(), 3.seconds)
      assert(result1.get === (0 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
      assert(result2.get === (20 until 10000).filter(_ % 2 == 0).map(_ + 1).sum)
    }

    it("onError should be emitted without asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = PublishSubject[Int]()

      subject.subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => result1.set(ex)
      )
      subject.subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => result2.set(ex)
      )

      Await.result(subject.onError(new RuntimeException("dummy")), 1.second)

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      @volatile var wasCompleted = false
      subject.subscribeUnit(_ => (), _ => (), () => { wasCompleted = true })
      assert(wasCompleted === true)
    }

    it("onError should be emitted over asynchronous boundaries") {
      val result1 = Atomic(null : Throwable)
      val result2 = Atomic(null : Throwable)

      val subject = PublishSubject[Int]()

      subject.flatMap(x => Observable.unit(x)).subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => result1.set(ex)
      )
      subject.flatMap(x => Observable.unit(x)).subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => result2.set(ex)
      )

      subject.onNext(1)
      Await.result(subject.onError(new RuntimeException("dummy")), 1.second)

      assert(result1.get != null && result1.get.getMessage == "dummy")
      assert(result2.get != null && result2.get.getMessage == "dummy")

      @volatile var wasCompleted = false
      subject.subscribeUnit(_ => (), _ => (), () => { wasCompleted = true })
      assert(wasCompleted === true)
    }

    it("onComplete should be emitted without asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()

      subject.subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => (),
        completedFn = () => result1.set(1)
      )
      subject.subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => (),
        completedFn = () => result2.set(2)
      )

      Await.result(subject.onCompleted(), 1.second)

      assert(result1.get === 1)
      assert(result2.get === 2)

      @volatile var wasCompleted = false
      subject.subscribeUnit(_ => (), _ => (), () => { wasCompleted = true })
      assert(wasCompleted === true)
    }

    it("onComplete should be emitted over asynchronous boundaries") {
      val result1 = Atomic(0)
      val result2 = Atomic(0)

      val subject = PublishSubject[Int]()

      subject.flatMap(x => Observable.unit(x)).subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => (),
        completedFn = () => result1.set(1)
      )
      subject.flatMap(x => Observable.unit(x)).subscribeUnit(
        nextFn = elem => (),
        errorFn = ex => (),
        completedFn = () => result2.set(2)
      )

      subject.onNext(1)
      Await.result(subject.onCompleted(), 1.second)

      assert(result1.get === 1)
      assert(result2.get === 2)

      @volatile var wasCompleted = false
      subject.subscribeUnit(_ => (), _ => (), () => { wasCompleted = true })
      assert(wasCompleted === true)
    }
  }
}
