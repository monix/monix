package monifu.rx

import org.scalatest.FunSuite
import concurrent.duration._
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.atomic.Atomic
import scala.concurrent.{Await, Promise}

class ObservableTest extends FunSuite {
  import monifu.concurrent.Scheduler.Implicits.computation

  test("interval") {
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

  test("detaches subscriptions") {
    val atomic = Atomic(0)
    val obs = Observable.unitAsync(1).map(_ + 1).filter(_ % 2 == 0)
    val sub = obs.subscribe(x => atomic.set(x))
    atomic.waitForValue(2, 1.second)

    assert(sub.asInstanceOf[BooleanCancelable].isCanceled === true)
  }

  test("detaches subscriptions on generated interval") {
    val atomic = Atomic(0L)
    val obs = Observable.interval(10.millis)
      .takeWhile(_ < 10)
      .foldLeft(0L)(_ + _)

    val promise = Promise[Long]()
    val sub = obs.subscribe(x => atomic.set(x), err => promise.failure(err), () => {
      computation.scheduleOnce(50.millis, promise.success(atomic.get))
    })

    val f = promise.future
    val result = Await.result(f, 1.second)

    assert(result === 45)
    assert(sub.asInstanceOf[BooleanCancelable].isCanceled === true)
  }
}
