package monifu.concurrent.cancelables

import org.scalatest.FunSuite

class RefCountCancelableTest extends FunSuite {
  test("cancel without dependent references") {
    var isCanceled = false
    val sub = RefCountCancelable { isCanceled = true }
    sub.cancel()

    assert(isCanceled === true)
  }

  test("execute onCancel with no dependent refs active") {
    var isCanceled = false
    val sub = RefCountCancelable { isCanceled = true }

    val s1 = sub.acquire()
    val s2 = sub.acquire()
    s1.cancel()
    s2.cancel()

    assert(isCanceled === false)
    assert(sub.isCanceled === false)

    sub.cancel()

    assert(isCanceled === true)
    assert(sub.isCanceled === true)
  }

  test("execute onCancel only after all dependent refs have been canceled") {
    var isCanceled = false
    val sub = RefCountCancelable { isCanceled = true }

    val s1 = sub.acquire()
    val s2 = sub.acquire()
    sub.cancel()

    assert(sub.isCanceled === true)
    assert(isCanceled === false)
    s1.cancel()
    assert(isCanceled === false)
    s2.cancel()
    assert(isCanceled === true)
  }
}
