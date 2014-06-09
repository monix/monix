package monifu.reactive

import org.scalatest.FunSpec
import monifu.reactive.subjects.AsyncSubject
import monifu.concurrent.Scheduler.Implicits.global
import java.util.concurrent.{TimeUnit, CountDownLatch}

class AsyncSubjectTest extends FunSpec {
  describe("AsyncSubject") {
    it("should emit the last value for already subscribed observers") {
      val subject = AsyncSubject[Int]()
      var received = 0
      subject.foreach(x => if (received == 0) received = x)

      subject.onNext(1)
      subject.onNext(2)
      subject.onComplete()

      assert(received === 2)
    }

    it("should emit the last value after completed") {
      val subject = AsyncSubject[Int]()
      var received = 0

      subject.onNext(1)
      subject.onNext(2)
      subject.onComplete()

      subject.foreach(x => if (received == 0) received = x)
      assert(received === 2)
    }

    it("should not emit anything to active subscribers in case is empty") {
      val subject = AsyncSubject[Int]()
      val latch = new CountDownLatch(1)

      subject.subscribe(
        x => throw new IllegalStateException("onNext should not happen"),
        ex => throw new IllegalStateException(s"onError($ex) should not happen"),
        () => latch.countDown()
      )

      subject.onComplete()
      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should not emit anything after completed for new subscribers, if empty") {
      val subject = AsyncSubject[Int]()
      val latch = new CountDownLatch(1)
      subject.onComplete()

      subject.subscribe(
        x => throw new IllegalStateException("onNext should not happen"),
        ex => throw new IllegalStateException(s"onError($ex) should not happen"),
        () => latch.countDown()
      )

      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should emit error to active subscribers") {
      val subject = AsyncSubject[Int]()

      val latch = new CountDownLatch(1)

      subject.subscribe(
        x => throw new IllegalStateException("onNext should not happen"),
        ex => { assert(ex.getMessage === "dummy"); latch.countDown() },
        () => throw new IllegalStateException("onComplete should not happen")
      )

      subject.onNext(1)
      subject.onNext(2)
      subject.onError(new RuntimeException("dummy"))

      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should emit error to new subscribers after completion") {
      val subject = AsyncSubject[Int]()
      val latch = new CountDownLatch(1)

      subject.onNext(1)
      subject.onNext(2)
      subject.onError(new RuntimeException("dummy"))

      subject.subscribe(
        x => throw new IllegalStateException("onNext should not happen"),
        ex => { assert(ex.getMessage === "dummy"); latch.countDown() },
        () => throw new IllegalStateException("onComplete should not happen")
      )

      assert(latch.await(3, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }
}
