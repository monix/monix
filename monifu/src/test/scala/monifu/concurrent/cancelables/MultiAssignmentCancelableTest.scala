package monifu.concurrent.cancelables

import org.scalatest.FunSuite

class MultiAssignmentCancelableTest extends FunSuite {
  test("cancel()") {
    var effect = 0
    val sub = BooleanCancelable(effect += 1)
    val mSub = MultiAssignmentCancelable(sub)

    assert(effect === 0)
    assert(!sub.isCanceled)
    assert(!mSub.isCanceled)

    mSub.cancel()
    assert(sub.isCanceled && mSub.isCanceled)
    assert(effect === 1)

    mSub.cancel()
    assert(sub.isCanceled && mSub.isCanceled)
    assert(effect === 1)
  }

  test("cancel() after second assignment") {
    var effect = 0
    val sub = BooleanCancelable(effect += 1)
    val mSub = MultiAssignmentCancelable(sub)
    val sub2 = BooleanCancelable(effect += 10)
    mSub() = sub2

    assert(effect === 0)
    assert(!sub.isCanceled && !sub2.isCanceled && !mSub.isCanceled)

    mSub.cancel()
    assert(sub2.isCanceled && mSub.isCanceled && !sub.isCanceled)
    assert(effect === 10)
  }

  test("automatically cancel assigned") {
    val mSub = MultiAssignmentCancelable()
    mSub.cancel()

    var effect = 0
    val sub = BooleanCancelable(effect += 1)

    assert(effect === 0)
    assert(!sub.isCanceled && mSub.isCanceled)

    mSub() = sub
    assert(effect === 1)
    assert(sub.isCanceled)
  }
}
