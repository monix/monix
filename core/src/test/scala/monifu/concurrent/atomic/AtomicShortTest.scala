package monifu.concurrent.atomic

import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class AtomicShortTest extends FunSuite {
  test("set()") {
    val r = Atomic(11.toShort)
    assert(r.get === 11)

    r.set(100)
    assert(r.get === 100)
    r.lazySet(255)
    assert(r.get === 255)
  }

  test("getAndSet()") {
    val r = Atomic(100.toShort)
    assert(r.getAndSet(200.toShort) === 100.toShort)
    assert(r.getAndSet(0) === 200.toShort)
    assert(r.get === 0)
  }

  test("compareAndSet()") {
    val r = Atomic(0.toShort)

    assert(r.compareAndSet(0.toShort, 100.toShort), "CAS should succeed")
    assert(r.get === 100.toShort)
    assert(!r.compareAndSet(0.toShort, 200.toShort), "CAS should fail")
    assert(r.get === 100.toShort)
    assert(r.compareAndSet(100.toShort, 200.toShort), "CAS should succeed")
    assert(r.get === 200.toShort)
  }

  test("weakCompareAndSet()") {
    val r = Atomic(0.toShort)

    assert(r.weakCompareAndSet(0.toShort, 100.toShort), "CAS should succeed")
    assert(r.get === 100.toShort)
    assert(!r.weakCompareAndSet(0.toShort, 200.toShort), "CAS should fail")
    assert(r.get === 100.toShort)
    assert(r.weakCompareAndSet(100.toShort, 200.toShort), "CAS should succeed")
    assert(r.get === 200.toShort)
  }

  test("increment()") {
    val r = Atomic(0.toShort)
    assert(r.get === 0)

    r.increment
    assert(r.get === 1)
    r.increment
    assert(r.get === 2)

    for (i <- 0 until 253 + 255 * 256)
      r.increment

    assert(r.get === -1)
    r.increment
    assert(r.get === 0)
    r.increment
    assert(r.get === 1)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("increment(v)") {
    val r = Atomic(0.toShort)
    assert(r.get === 0)

    r.increment(2.toShort)
    assert(r.get === 2)

    r.increment((Short.MaxValue - 2).toShort)
    assert(r.get === Short.MaxValue)
    r.increment(2.toShort)
    assert(r.get === Short.MinValue + 1)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment(2.toShort))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("decrement()") {
    val r = Atomic(1.toShort)
    assert(r.get === 1)

    r.decrement
    assert(r.get === 0)
    r.decrement
    assert(r.get === -1)
    r.decrement
    assert(r.get === -2)
    for (i <- 0 until 254 + 255 * 256)
      r.decrement
    assert(r.get === 0)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000)
  }

  test("decrement(v)") {
    val r = Atomic(1.toShort)
    assert(r.get === 1)

    r.decrement(3.toShort)
    assert(r.get === -2)

    r.decrement(Short.MaxValue)
    assert(r.get === Short.MaxValue)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement(2.toShort))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get.toInt === -2000)
  }


  test("incrementAndGet()") {
    val r = Atomic((Short.MaxValue - 2).toShort)
    assert(r.incrementAndGet === Short.MaxValue - 1)
    assert(r.incrementAndGet === Short.MaxValue)
    assert(r.incrementAndGet === Short.MinValue)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("getAndIncrement()") {
    val r = Atomic(126.toShort)
    assert(r.getAndIncrement === 126)
    assert(r.getAndIncrement === 127)
    assert(r.getAndIncrement === 128)
    assert(r.get === 129)

    val r2 = Atomic(Short.MaxValue)
    assert(r2.getAndIncrement === Short.MaxValue)
    assert(r2.get === Short.MinValue)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("decrementAndGet()") {
    val r = Atomic((Short.MinValue + 2).toShort)
    assert(r.decrementAndGet === Short.MinValue + 1)
    assert(r.decrementAndGet === Short.MinValue)
    assert(r.decrementAndGet === Short.MaxValue)
    assert(r.decrementAndGet === Short.MaxValue - 1)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000)
  }

  test("getAndDecrement()") {
    val r = Atomic((Short.MinValue + 2).toShort)
    assert(r.getAndDecrement === Short.MinValue + 2)
    assert(r.getAndDecrement === Short.MinValue + 1)
    assert(r.getAndDecrement === Short.MinValue)
    assert(r.getAndDecrement === Short.MaxValue)
    assert(r.getAndDecrement === Short.MaxValue - 1)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000)
  }

  test("incrementAndGet(v)") {
    val r = Atomic((Short.MaxValue - 4).toShort)
    assert(r.incrementAndGet(2.toShort) === Short.MaxValue - 2)
    assert(r.incrementAndGet(2.toShort) === Short.MaxValue)
    assert(r.incrementAndGet(2.toShort) === Short.MinValue + 1)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet(2.toShort))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("getAndIncrement(v)") {
    val r = Atomic((Short.MaxValue - 4).toShort)
    assert(r.getAndIncrement(2.toShort) === Short.MaxValue - 4)
    assert(r.getAndIncrement(2.toShort) === Short.MaxValue - 2)
    assert(r.getAndIncrement(2.toShort) === Short.MaxValue)
    assert(r.getAndIncrement(2.toShort) === Short.MinValue + 1)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement(2.toShort))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("decrementAndGet(v)") {
    val r = Atomic((Short.MinValue + 2).toShort)
    assert(r.decrementAndGet(2.toShort) === Short.MinValue)
    assert(r.decrementAndGet(2.toShort) === Short.MaxValue - 1)
    assert(r.decrementAndGet(2.toShort) === Short.MaxValue - 3)
    assert(r.decrementAndGet(2.toShort) === Short.MaxValue - 5)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet(2.toShort))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000)
  }

  test("getAndDecrement(v)") {
    val r = Atomic((Short.MinValue + 2).toShort)
    assert(r.getAndDecrement(2.toShort) === Short.MinValue + 2)
    assert(r.getAndDecrement(2.toShort) === Short.MinValue)
    assert(r.getAndDecrement(2.toShort) === Short.MaxValue - 1)
    assert(r.getAndDecrement(2.toShort) === Short.MaxValue - 3)
    assert(r.getAndDecrement(2.toShort) === Short.MaxValue - 5)

    val rs = Atomic(0.toShort)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement(2.toShort))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000)
  }
}
