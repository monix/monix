package monifu.reactive

import org.scalatest.FunSpec
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import monifu.concurrent.Scheduler.Implicits.global
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.reactive.api.Ack.Continue
import monifu.reactive.api.BufferPolicy.{OverflowTriggering, BackPressured, Unbounded}
import monifu.reactive.api.Notification
import monifu.reactive.api.Notification.{OnComplete, OnNext}
import monifu.reactive.subjects.BehaviorSubject

/**
 * Tests involving the Observable operators when used on `Observable.unit`.
 */
class ObservableOperatorsOnUnitTest extends FunSpec {
  describe("Observable.unit") {
    it("should map") {
      val f = Observable.unit(1).map(_ + 2).asFuture
      assert(Await.result(f, 2.seconds) === Some(3))
    }

    it("should filter") {
      val f1 = Observable.unit(1).filter(_ % 2 == 0).asFuture
      assert(Await.result(f1, 2.seconds) === None)

      val f2 = Observable.unit(1).filter(_ % 2 == 1).asFuture
      assert(Await.result(f2, 2.seconds) === Some(1))
    }

    it("should flatMap") {
      val f = Observable.unit(1).flatMap(x => Observable.unit(x + 2)).asFuture
      assert(Await.result(f, 2.seconds) === Some(3))
    }

    it("should mergeMap") {
      val f = Observable.unit(1).mergeMap(x => Observable.unit(x + 2)).asFuture
      assert(Await.result(f, 2.seconds) === Some(3))
    }

    it("should unsafeMerge") {
      val f = Observable.unit(1).map(x => Observable.unit(x + 2)).unsafeMerge.asFuture
      assert(Await.result(f, 2.seconds) === Some(3))
    }

    it("should take") {
      val f1 = Observable.unit(1).take(1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(1))

      val f2 = Observable.unit(1).take(10).asFuture
      assert(Await.result(f2, 2.seconds) === Some(1))

      val f3 = Observable.unit(1).take(0).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should takeRight") {
      val f1 = Observable.unit(1).takeRight(1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(1))

      val f2 = Observable.unit(1).takeRight(10).asFuture
      assert(Await.result(f2, 2.seconds) === Some(1))

      val f3 = Observable.unit(1).takeRight(0).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should drop") {
      val f1 = Observable.unit(1).drop(0).asFuture
      assert(Await.result(f1, 2.seconds) === Some(1))

      val f2 = Observable.unit(1).drop(1).asFuture
      assert(Await.result(f2, 2.seconds) === None)

      val f3 = Observable.unit(1).drop(10).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should takeWhile") {
      val f1 = Observable.unit(1).takeWhile(_ % 2 == 1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(1))

      val f2 = Observable.unit(1).takeWhile(_ % 2 == 0).asFuture
      assert(Await.result(f2, 2.seconds) === None)
    }

    it("should dropWhile") {
      val f1 = Observable.unit(1).dropWhile(_ == 2).asFuture
      assert(Await.result(f1, 2.seconds) === Some(1))

      val f2 = Observable.unit(1).dropWhile(_ == 1).asFuture
      assert(Await.result(f2, 2.seconds) === None)
    }

    it("should buffer(count)") {
      val f1 = Observable.unit(1).buffer(1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(Seq(1)))

      val f2 = Observable.unit(1).buffer(2).asFuture
      assert(Await.result(f2, 2.seconds) === Some(Seq(1)))
    }

    it("should buffer(timestamp)") {
      val f = Observable.unit(1).buffer(200.millis).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(1)))
    }

    it("should foldLeft") {
      val f = Observable.unit(1).foldLeft(1)(_+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(2))
    }

    it("should reduce") {
      val f = Observable.unit(1).reduce(_+_).asFuture
      assert(Await.result(f, 2.seconds) === None)
    }

    it("should scan") {
      val f = Observable.unit(1).scan(2)(_+_).foldLeft(0)(_+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(3))
    }

    it("should flatScan") {
      val f = Observable.unit(1).flatScan(2)((a,b) => Future(a+b)).foldLeft(0)(_+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(3))
    }

    it("should doOnComplete") {
      val latch = new CountDownLatch(1)
      Observable.unit(1).doOnComplete(latch.countDown()).subscribe()
      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should doWork") {
      var seen = 0
      val latch = new CountDownLatch(1)
      Observable.unit(1).doWork(x => seen = x).doOnComplete(latch.countDown()).subscribe()
      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(seen === 1)
    }

    it("should doOnStart") {
      var seen = 0
      val latch = new CountDownLatch(1)
      Observable.unit(1).doOnStart(x => seen = x).doOnComplete(latch.countDown()).subscribe()
      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(seen === 1)
    }

    it("should find") {
      val f1 = Observable.unit(1).find(_ === 1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(1))

      val f2 = Observable.unit(1).find(_ === 2).asFuture
      assert(Await.result(f2, 2.seconds) === None)
    }

    it("should exists") {
      val f1 = Observable.unit(1).exists(_ === 1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(true))

      val f2 = Observable.unit(1).exists(_ === 2).asFuture
      assert(Await.result(f2, 2.seconds) === Some(false))
    }

    it("should forAll") {
      val f1 = Observable.unit(1).forAll(_ === 1).asFuture
      assert(Await.result(f1, 2.seconds) === Some(true))

      val f2 = Observable.unit(1).forAll(_ === 2).asFuture
      assert(Await.result(f2, 2.seconds) === Some(false))
    }

    it("should complete") {
      val f = Observable.unit(1).complete.asFuture
      assert(Await.result(f, 2.seconds) === None)
    }

    it("should error") {
      val f = Observable.unit(1).map(_ => throw new RuntimeException("dummy")).error.asFuture
      val ex = Await.result(f, 2.seconds)
      assert(ex.isDefined && ex.get.getMessage == "dummy", s"exception test failed, $ex returned")
    }

    it("should endWithError") {
      var seen = 0
      val latch = new CountDownLatch(1)

      Observable.unit(1).endWithError(new RuntimeException("dummy")).subscribe(
        elem => { seen = elem; Continue },
        exception => {
          assert(exception.getMessage === "dummy")
          latch.countDown()
        }
      )

      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(seen === 1)
    }

    it("should +:") {
      val twoElems = 1 +: Observable.unit(2)
      val f = twoElems.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(1,2)))
    }

    it("should startWith") {
      val multiple = Observable.unit(4).startWith(1,2,3)
      val f = multiple.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(1,2,3,4)))
    }

    it("should :+") {
      val twoElems = Observable.unit(1) :+ 2
      val f = twoElems.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(1,2)))
    }

    it("should endWith") {
      val multiple = Observable.unit(1).endWith(2,3,4)
      val f = multiple.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(1,2,3,4)))
    }

    it("should ++") {
      val twoElems = Observable.unit(1) ++ Observable.unit(2)
      val f = twoElems.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(1,2)))
    }

    it("should head") {
      val f = Observable.unit(1).head.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(Seq(1)))
    }

    it("should tail") {
      val f = Observable.unit(1).tail.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(Seq.empty))
    }

    it("should last") {
      val f = Observable.unit(1).last.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(Seq(1)))
    }

    it("should headOrElse") {
      val f1 = Observable.unit(1).headOrElse(2).foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f1, 2.seconds) === Some(Seq(1)))

      val f2 = Observable.unit(1).filter(_ => false).headOrElse(2).foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f2, 2.seconds) === Some(Seq(2)))
    }

    it("should zip") {
      val f = Observable.unit(1).zip(Observable.unit(2)).asFuture
      assert(Await.result(f, 5.seconds) === Some((1,2)))
    }

    it("should max") {
      val f = Observable.unit(1).max.asFuture
      assert(Await.result(f, 5.seconds) === Some(1))
    }

    it("should maxBy") {
      case class Person(age: Int)
      val f = Observable.unit(Person(32)).maxBy(_.age).asFuture
      assert(Await.result(f, 5.seconds) === Some(Person(32)))
    }

    it("should min") {
      val f = Observable.unit(1).min.asFuture
      assert(Await.result(f, 5.seconds) === Some(1))
    }

    it("should minBy") {
      case class Person(age: Int)
      val f = Observable.unit(Person(32)).minBy(_.age).asFuture
      assert(Await.result(f, 5.seconds) === Some(Person(32)))
    }

    it("should sum") {
      val f = Observable.unit(1).sum.asFuture
      assert(Await.result(f, 5.seconds) === Some(1))
    }

    it("should observeOn") {
      val f1 = Observable.unit(1).observeOn(global, Unbounded).sum.asFuture
      assert(Await.result(f1, 5.seconds) === Some(1))

      val f2 = Observable.unit(1).observeOn(global, BackPressured(2)).sum.asFuture
      assert(Await.result(f2, 5.seconds) === Some(1))

      val f3 = Observable.unit(1).observeOn(global, BackPressured(1000)).sum.asFuture
      assert(Await.result(f3, 5.seconds) === Some(1))

      val f4 = Observable.unit(1).observeOn(global, OverflowTriggering(2)).sum.asFuture
      assert(Await.result(f4, 5.seconds) === Some(1))

      val f5 = Observable.unit(1).observeOn(global, OverflowTriggering(1000)).sum.asFuture
      assert(Await.result(f5, 5.seconds) === Some(1))
    }

    it("should distinct") {
      val f1 = Observable.unit(1).distinct.asFuture
      assert(Await.result(f1, 5.seconds) === Some(1))

      val f2 = Observable.unit(1).distinct(_ + 1).asFuture
      assert(Await.result(f2, 5.seconds) === Some(1))
    }

    it("should distinctUntilChanged") {
      val f1 = Observable.unit(1).distinctUntilChanged.asFuture
      assert(Await.result(f1, 5.seconds) === Some(1))

      val f2 = Observable.unit(1).distinctUntilChanged(x => x + 1).asFuture
      assert(Await.result(f2, 5.seconds) === Some(1))
    }

    it("should subscribeOn") {
      val f = Observable.unit(1).subscribeOn(global).asFuture
      assert(Await.result(f, 5.seconds) === Some(1))
    }

    it("should materialize") {
      val f = Observable.unit(1).materialize.foldLeft(Seq.empty[Notification[Int]])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(Seq(OnNext(1), OnComplete)))
    }

    it("should repeat") {
      val f = Observable.unit(1).repeat.take(1000).sum.asFuture
      assert(Await.result(f, 10.seconds) === Some(1000))
    }

    it("should multicast") {
      for (_ <- 0 until 1000) {
        var seen = 0
        val completed = new CountDownLatch(1)

        val connectable = Observable.unit(1).multicast(BehaviorSubject(1))
        connectable.doOnComplete(completed.countDown()).sum.foreach(x => seen = x)

        assert(seen === 0)
        assert(completed.getCount === 1)

        connectable.connect()
        assert(completed.await(1, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 2)
      }
    }

    it("should publish") {
      var seen = 0
      val completed = new CountDownLatch(1)

      val connectable = Observable.unit(1).publish()
      connectable.doOnComplete(completed.countDown()).sum.foreach(x => seen = x)

      assert(seen === 0)
      assert(!completed.await(100, TimeUnit.MILLISECONDS), "connectable.await should have failed")

      connectable.connect()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(seen === 1)
    }

    it("should behavior") {
      var seen = 0
      val completed = new CountDownLatch(1)

      val connectable = Observable.unit(1).behavior(1)
      connectable.doOnComplete(completed.countDown()).sum.foreach(x => seen = x)

      assert(seen === 0)
      assert(!completed.await(100, TimeUnit.MILLISECONDS), "connectable.await should have failed")

      connectable.connect()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(seen === 2)
    }

    it("should replay") {
      var seen = 0
      val completed = new CountDownLatch(1)

      val connectable = Observable.unit(1).replay()
      connectable.connect()

      connectable.doOnComplete(completed.countDown()).sum.foreach(x => seen = x)
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(seen === 1)
    }
  }
}
