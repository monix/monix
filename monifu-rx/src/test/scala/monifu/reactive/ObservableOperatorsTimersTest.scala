package monifu.reactive

import org.scalatest.FunSpec

import scala.concurrent._
import scala.concurrent.duration._
import monifu.concurrent.Scheduler.Implicits.global

/**
 * Tests involving the Observable operators when used with time specific operations.
 */
class ObservableOperatorsTimersTest extends FunSpec{
  describe("Observable.timers") {
    it("should timeout") {
      try {
        Await.result(Observable.never.timeout(0.seconds, 1.second).asFuture, 2.seconds)
      } catch {
        case timeout: Throwable =>
          assert(timeout.isInstanceOf[TimeoutException], timeout)
      }
    }
  }
}
