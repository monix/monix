package monifu.rx

import concurrent.duration._
import monifu.concurrent.cancelables.CompositeCancelable
import monifu.concurrent.atomic.Atomic
import scala.concurrent.{Await, Promise}
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.test.MonifuTest
import monifu.concurrent.Cancelable

class ObservableTest extends MonifuTest {
  import monifu.concurrent.Scheduler.Implicits.computation

  describe("Observable resource management") {
    it("should do interval") {
      val startAt = System.nanoTime()
      val f = Observable.interval(10.millis)
        .takeWhile(_ < 10)
        .foldLeft(0L)(_ + _)
        .asFuture

      val result = Await.result(f, 1.second)
      val endedAt = System.nanoTime()

      assert(result === Some(9 * 5))
      assert((endedAt - startAt).nanos >= 100.millis)
    }

    it("should cancels subscriptions onCompleted") {
      val latch = new CountDownLatch(1)
      val obs1 = Observable.unit(0)
      val obs2 = Observable[Int] { observer =>
        val composite = CompositeCancelable()
        composite += computation.scheduleOnce {
          observer.onNext(10)
          observer.onCompleted()
        }
        composite += Cancelable {
          latch.countDown()
        }
        composite
      }

      val obs = obs1 ++ obs2

      var effect = 0
      val sub = obs.map(_ * 2).subscribe(x => effect = x)
      latch.await(1, TimeUnit.SECONDS)

      assert(sub.isCanceled === true)
      assert(effect === 20)
    }

    it("should trigger onComplete on takeWhile") {
      val atomic = Atomic(0L)
      val obs = Observable.interval(10.millis)
        .takeWhile(_ < 10)
        .foldLeft(0L)(_ + _)
        .filter(_ => true)

      val promise = Promise[Long]()
      val sub = obs.subscribe(x => atomic.set(x), err => promise.failure(err), () => {
        computation.scheduleOnce(50.millis, promise.success(atomic.get))
      })

      val f = promise.future
      val result = Await.result(f, 1.second)

      assert(result === 45)
      assert(sub.isCanceled === true)
    }
  }

  describe("Observable combinators") {
    it("should flatMap") {
      val f = Observable.interval(1.millis)
        .filter(_ % 5 == 1)
        .takeWhile(_ <= 11)
        .flatMap(x => Observable.interval(10.millis).filter(_ >= x).takeWhile(_ < x + 5))
        .foldLeft(Seq.empty[Long])(_ :+ _)
        .map(_.sorted)
        .asFuture

      val result = Await.result(f, 3.seconds)
      assert(result === Some((1 to 15).toSeq))
    }

    it("should ++") {
      val obs1 = Observable.fromSequence(0 until 1000).filter(_ % 2 == 0).takeWhile(_ < 100).subscribeOn(computation)
      val obs2 = Observable.fromSequence(0 until 1000).filter(_ % 2 == 0).filter(_ >= 100).takeWhile(_ < 200).subscribeOn(computation)
      val obs3 = obs1 ++ obs2

      val f = obs3.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val result = Await.result(f, 3.seconds)

      assert(result === Some(0.until(200, 2)))
    }
  }
}
