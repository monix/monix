package monifu.concurrent.atomic

import org.scalatest.FunSuite

class AtomicBooleanTest extends FunSuite {
  test("set()") {
    val r = Atomic(initialValue = true)
    assert(r() === true)
    r.set(update = false)
    assert(r() === false)
    r.set(update = true)
    assert(r() === true)
  }

  test("lazySet()") {
    val r = Atomic(initialValue = true)
    assert(r() === true)
    r.lazySet(update = false)
    assert(r() === false)
    r.lazySet(update = true)
    assert(r() === true)
  }

  test("getAndSet()") {
    val r = Atomic(initialValue = true)
    assert(r.get === true)

    assert(r.getAndSet(update = false) === true)
    assert(r.get === false)
  }

  test("compareAndSet()") {
    val r = Atomic(initialValue = true)
    assert(r.get === true)

    assert(r.compareAndSet(expect = true, update = false) === true)
    assert(r.get === false)
    assert(r.compareAndSet(expect = true, update = true) === false)
    assert(r.get === false)
    assert(r.compareAndSet(expect = false, update = true) === true)
    assert(r.get === true)
  }

  test("weakCompareAndSet()") {
    val r = Atomic(initialValue = true)
    assert(r.get === true)

    assert(r.weakCompareAndSet(expect = true, update = false) === true)
    assert(r.get === false)
    assert(r.weakCompareAndSet(expect = true, update = true) === false)
    assert(r.get === false)
    assert(r.weakCompareAndSet(expect = false, update = true) === true)
    assert(r.get === true)
  }
}
