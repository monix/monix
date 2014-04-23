package monifu.concurrent

import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import concurrent.duration._
import java.util.concurrent.TimeoutException
import monifu.concurrent.extensions._

class FutureExtensionsTest extends FunSuite {
  import Scheduler.Implicits.global

  test("delayedResult") {
    val startedAt = System.nanoTime()
    val f = Future.delayedResult(100.millis)("TICK")

    assert(Await.result(f, 1.second) === "TICK")
    assert((System.nanoTime() - startedAt).nanos >= 100.millis)
  }

  test("withTimeout should succeed") {
    val f = Future.delayedResult(50.millis)("Hello world!")
    val t = f.withTimeout(300.millis)

    assert(Await.result(t, 1.second) === "Hello world!")
  }

  test("withTimeout should fail") {
    val f = Future.delayedResult(1.second)("Hello world!")
    val t = f.withTimeout(30.millis)

    intercept[TimeoutException] {
      Await.result(t, 1.second)
    }
  }
}
