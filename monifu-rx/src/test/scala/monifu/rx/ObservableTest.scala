package monifu.rx

import org.scalatest.FunSuite
import concurrent.duration._
import scala.concurrent.Await

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
}
