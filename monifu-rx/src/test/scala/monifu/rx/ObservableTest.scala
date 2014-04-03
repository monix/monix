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

  describe("Observable") {
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

    it("should cancel subscription on complex expression") {
      val obs = Observable.interval(1.millis)
        .filter(_ % 5 == 1)
        .takeWhile(_ <= 11)
        .flatMap(x => Observable.interval(10.millis).filter(_ >= x).takeWhile(_ < x + 5))
        .foldLeft(Seq.empty[Long])(_ :+ _)
        .map(_.sorted)

      val latch = new CountDownLatch(1)
      var value = Seq.empty[Long]
      val sub = obs.subscribe(
        elem => value = elem,
        error => throw error,
        () => latch.countDown()
      )

      latch.await(5, TimeUnit.SECONDS)
      expect(sub.isCanceled).toBe(true)
      expect(value).toBe((1 to 15).map(_.toLong))
    }

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

    it("should synchronize ++") {
      val sequence = (0 until 100).map(x => Observable.unit(x).subscribeOn(computation))
      val concat = sequence.foldLeft(Observable.empty[Int])(_ ++ _).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val result = Await.result(concat, 3.seconds)

      expect(result).toBe(Some(0 until 100))
    }

    it("should map") {
      @volatile var effect = 0
      val latch = new CountDownLatch(1)
      val sub = Observable.unit(100).subscribeOn(computation).map(_ * 2).subscribe(
        e => effect = e,
        err => throw err,
        () => latch.countDown()
      )

      latch.await(1, TimeUnit.SECONDS)
      expect(effect).toBe(200)
      expect(sub.isCanceled).toBe(true)
    }

    it("should filter") {
      @volatile var effect = 0
      val latch = new CountDownLatch(1)
      val sub = Observable.fromSequence(0 to 10).subscribeOn(computation).filter(_ >= 5).foldLeft(0)(_+_)
        .subscribe(
          e => effect = e,
          err => throw err,
          () => latch.countDown()
        )

      latch.await(1, TimeUnit.SECONDS)
      expect(effect).toBe(10 * 11 / 2 - 4 * 5 / 2)
      expect(sub.isCanceled).toBe(true)
    }

    it("should takeWhile and dropWhile") {
      @volatile var effect = Seq.empty[Long]
      val latch = new CountDownLatch(1)
      val sub = Observable.interval(1.millis).dropWhile(_ < 100).takeWhile(_ < 200).foldLeft(Seq.empty[Long])(_:+_)
        .subscribe(
          e => effect = e,
          err => throw err,
          () => latch.countDown()
        )

      latch.await(1, TimeUnit.SECONDS)
      expect(effect.sorted).toBe((100 until 200).map(_.toLong).sorted)
      expect(sub.isCanceled).toBe(true)
    }

    it("should take and drop") {
      @volatile var effect = Seq.empty[Long]
      val latch = new CountDownLatch(1)
      val sub = Observable.interval(1.millis).drop(100).take(100).foldLeft(Seq.empty[Long])(_:+_)
        .subscribe(
          e => effect = e,
          err => throw err,
          () => latch.countDown()
        )

      latch.await(1, TimeUnit.SECONDS)
      expect(effect.sorted).toBe((100 until 200).map(_.toLong).sorted)
      expect(sub.isCanceled).toBe(true)
    }

    it("should flatMap like a boss") {
      def merge[T, U](obs: Observable[T])(f: T => Observable[U]): Observable[U] =
        obs.head.flatMap(f) ++ merge(obs.tail)(f)

      val obs = merge(Observable.interval(1.millis))(x => Observable.fromSequence((x * 5) until (x * 5 + 5)).subscribeOn(computation))
      @volatile var effect = Seq.empty[Long]
      val latch = new CountDownLatch(1)

      val sub = obs.take(100).foldLeft(effect)(_:+_).subscribe(
        e => effect = e,
        err => throw err,
        () => latch.countDown()
      )

      latch.await()
      expect(sub.isCanceled).toBe(true)
      expect(effect).toBe((0 until 100).map(_.toLong))
    }
  }
}
