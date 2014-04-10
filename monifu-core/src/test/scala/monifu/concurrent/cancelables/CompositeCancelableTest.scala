package monifu.concurrent.cancelables

import org.scalatest.FunSuite
import monifu.concurrent.Cancelable

class CompositeCancelableTest extends FunSuite {
  test("cancel") {
    val s = CompositeCancelable()
    val b1 = Cancelable()
    val b2 = Cancelable()
    s += b1
    s += b2
    s.cancel()

    assert(s.isCanceled === true)
    assert(b1.isCanceled === true)
    assert(b2.isCanceled === true)
  }

  test("cancel on assignment after being canceled") {
    val s = CompositeCancelable()
    val b1 = Cancelable()
    s += b1
    s.cancel()

    val b2 = Cancelable()
    s += b2

    assert(s.isCanceled === true)
    assert(b1.isCanceled === true)
    assert(b2.isCanceled === true)
  }
}
