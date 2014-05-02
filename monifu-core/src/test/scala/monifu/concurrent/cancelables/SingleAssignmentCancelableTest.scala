package monifu.concurrent.cancelables

import org.scalatest.FunSuite
import monifu.concurrent.Cancelable

class SingleAssignmentCancelableTest extends FunSuite {
  test("cancel()") {
    var effect = 0
    val s = SingleAssignmentCancelable()
    val b = BooleanCancelable { effect += 1 }
    s() = b

    s.cancel()
    assert(s.isCanceled === true)
    assert(b.isCanceled === true)
    assert(effect === 1)

    s.cancel()
    assert(effect === 1)
  }

  test("cancel on single assignment") {
    val s = SingleAssignmentCancelable()
    s.cancel()
    assert(s.isCanceled)

    var effect = 0
    val b = BooleanCancelable { effect += 1 }
    s() = b

    assert(b.isCanceled === true)
    assert(effect === 1)

    s.cancel()
    assert(effect === 1)
  }

  test("throw exception on multi assignment") {
    val s = SingleAssignmentCancelable()
    val b1 = Cancelable()
    s() = b1

    intercept[IllegalStateException] {
      val b2 = Cancelable()
      s() = b2
    }
  }

  test("throw exception on multi assignment when canceled") {
    val s = SingleAssignmentCancelable()
    s.cancel()

    val b1 = Cancelable()
    s() = b1

    intercept[IllegalStateException] {
      val b2 = Cancelable()
      s() = b2
    }
  }
}
