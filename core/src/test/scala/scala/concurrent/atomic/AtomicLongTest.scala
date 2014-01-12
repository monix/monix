package monifu.concurrent.atomic

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[JUnitRunner])
class AtomicLongTest extends FunSuite {
  test("set()") {
    val r = Atomic(11l)
    assert(r.get === 11l)

    r.set(100l)
    assert(r.get === 100l)
    r.lazySet(255l)
    assert(r.get === 255l)
  }

  test("getAndSet()") {
    val r = Atomic(100l)
    assert(r.getAndSet(200l) === 100l)
    assert(r.getAndSet(0l) === 200l)
    assert(r.get === 0l)
  }

  test("compareAndSet()") {
    val r = Atomic(0l)

    assert(r.compareAndSet(0l, 100l), "CAS should succeed")
    assert(r.get === 100l)
    assert(!r.compareAndSet(0l, 200l), "CAS should fail")
    assert(r.get === 100l)
    assert(r.compareAndSet(100l, 200l), "CAS should succeed")
    assert(r.get === 200l)
  }

  test("weakCompareAndSet()") {
    val r = Atomic(0l)

    assert(r.weakCompareAndSet(0l, 100l), "CAS should succeed")
    assert(r.get === 100l)
    assert(!r.weakCompareAndSet(0l, 200l), "CAS should fail")
    assert(r.get === 100l)
    assert(r.weakCompareAndSet(100l, 200l), "CAS should succeed")
    assert(r.get === 200l)
  }

  test("add()") {
    val r = Atomic(0l)
    assert(r.get === 0l)

    r.increment
    assert(r.get === 1l)
    r.increment
    assert(r.get === 2l)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000l)
  }

  test("add(v)") {
    val r = Atomic(0l)
    assert(r.get === 0l)

    r.add(2l)
    assert(r.get === 2l)

    r.add(Long.MaxValue - 2l)
    assert(r.get === Long.MaxValue)
    r.add(2l)
    assert(r.get === Long.MinValue + 1)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.add(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000l)
  }

  test("subtract()") {
    val r = Atomic(1l)
    assert(r.get === 1l)

    r.decrement
    assert(r.get === 0l)
    r.decrement
    assert(r.get === -1l)
    r.decrement
    assert(r.get === -2l)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000l)
  }

  test("subtract(v)") {
    val r = Atomic(1l)
    assert(r.get === 1l)

    r.subtract(3l)
    assert(r.get === -2l)

    r.subtract(Long.MaxValue)
    assert(r.get === Long.MaxValue)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.subtract(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000l)
  }


  test("addAndGet()") {
    val r = Atomic(Long.MaxValue - 2)
    assert(r.incrementAndGet === Long.MaxValue - 1)
    assert(r.incrementAndGet === Long.MaxValue)
    assert(r.incrementAndGet === Long.MinValue)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000l)
  }

  test("getAndAdd()") {
    val r = Atomic(126l)
    assert(r.getAndIncrement === 126l)
    assert(r.getAndIncrement === 127l)
    assert(r.getAndIncrement === 128l)
    assert(r.get === 129l)

    val r2 = Atomic(Long.MaxValue)
    assert(r2.getAndIncrement === Long.MaxValue)
    assert(r2.get === Long.MinValue)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000l)
  }

  test("subtractAndGet()") {
    val r = Atomic(Long.MinValue + 2l)
    assert(r.decrementAndGet === Long.MinValue + 1)
    assert(r.decrementAndGet === Long.MinValue)
    assert(r.decrementAndGet === Long.MaxValue)
    assert(r.decrementAndGet === Long.MaxValue - 1)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000l)
  }

  test("getAndDecrement()") {
    val r = Atomic(Long.MinValue + 2l)
    assert(r.getAndDecrement === Long.MinValue + 2l)
    assert(r.getAndDecrement === Long.MinValue + 1l)
    assert(r.getAndDecrement === Long.MinValue)
    assert(r.getAndDecrement === Long.MaxValue)
    assert(r.getAndDecrement === Long.MaxValue - 1l)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000l)
  }

  test("addAndGet(v)") {
    val r = Atomic(Long.MaxValue - 4)
    assert(r.addAndGet(2) === Long.MaxValue - 2)
    assert(r.addAndGet(2) === Long.MaxValue)
    assert(r.addAndGet(2) === Long.MinValue + 1)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.addAndGet(2l))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000l)
  }

  test("getAndAdd(v)") {
    val r = Atomic(Long.MaxValue - 4)
    assert(r.getAndAdd(2) === Long.MaxValue - 4)
    assert(r.getAndAdd(2) === Long.MaxValue - 2)
    assert(r.getAndAdd(2) === Long.MaxValue)
    assert(r.getAndAdd(2) === Long.MinValue + 1)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndAdd(2l))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000l)
  }

  test("subtractAndGet(v)") {
    val r = Atomic(Long.MinValue + 2l)
    assert(r.subtractAndGet(2l) === Long.MinValue)
    assert(r.subtractAndGet(2l) === Long.MaxValue - 1l)
    assert(r.subtractAndGet(2l) === Long.MaxValue - 3l)
    assert(r.subtractAndGet(2l) === Long.MaxValue - 5l)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.subtractAndGet(2l))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000l)
  }

  test("getAndDecrement(v)") {
    val r = Atomic(Long.MinValue + 2l)
    assert(r.getAndDecrement(2) === Long.MinValue + 2l)
    assert(r.getAndDecrement(2) === Long.MinValue)
    assert(r.getAndDecrement(2) === Long.MaxValue - 1l)
    assert(r.getAndDecrement(2) === Long.MaxValue - 3l)
    assert(r.getAndDecrement(2) === Long.MaxValue - 5l)

    val rs = Atomic(0l)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000l)
  }
}
