package monifu.concurrent.atomic

import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class AtomicByteTest extends FunSuite {
  test("set()") {
    val r = Atomic(-11.toByte)
    assert(r.get === -11)

    r.set(100)
    assert(r.get === 100)
    r.lazySet(-127)
    assert(r.get === -127)
  }

  test("getAndSet()") {
    val r = Atomic(100.toByte)
    assert(r.getAndSet(200.toByte) === 100.toByte)
    assert(r.getAndSet(0) === 200.toByte)
    assert(r.get === 0)
  }

  test("compareAndSet()") {
    val r = Atomic(0.toByte)

    assert(r.compareAndSet(0.toByte, 100.toByte), "CAS should succeed")
    assert(r.get === 100.toByte)
    assert(!r.compareAndSet(0.toByte, 200.toByte), "CAS should fail")
    assert(r.get === 100.toByte)
    assert(r.compareAndSet(100.toByte, 200.toByte), "CAS should succeed")
    assert(r.get === 200.toByte)
  }

  test("weakCompareAndSet()") {
    val r = Atomic(0.toByte)

    assert(r.weakCompareAndSet(0.toByte, 100.toByte), "CAS should succeed")
    assert(r.get === 100.toByte)
    assert(!r.weakCompareAndSet(0.toByte, 200.toByte), "CAS should fail")
    assert(r.get === 100.toByte)
    assert(r.weakCompareAndSet(100.toByte, 200.toByte), "CAS should succeed")
    assert(r.get === 200.toByte)
  }

  test("increment()") {
    val r = Atomic(0.toByte)
    assert(r.get === 0)

    r.increment()
    assert(r.get === 1)
    r.increment()
    assert(r.get === 2)

    for (i <- 0 until 253)
      r.increment()

    assert(r.get === 255.toByte)
    r.increment()
    assert(r.get === 0)
    r.increment()
    assert(r.get === 1)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment())
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -24)
  }

  test("increment(v)") {
    val r = Atomic(0.toByte)
    assert(r.get === 0)

    r.increment(2.toByte)
    assert(r.get === 2)

    r.increment(253.toByte)
    assert(r.get === 255.toByte)
    r.increment(2.toByte)
    assert(r.get === 1)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment(2.toByte))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -48)
  }

  test("decrement()") {
    val r = Atomic(1.toByte)
    assert(r.get === 1)

    r.decrement()
    assert(r.get === 0)
    r.decrement()
    assert(r.get === 255.toByte)
    r.decrement()
    assert(r.get === 254.toByte)
    for (i <- 0 until 254)
      r.decrement()
    assert(r.get === 0)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement())
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 24)
  }

  test("decrement(v)") {
    val r = Atomic(1.toByte)
    assert(r.get === 1)

    r.decrement(3.toByte)
    assert(r.get === -2)

    r.decrement(254.toByte)
    assert(r.get === 0)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement(2.toByte))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 48)
  }


  test("incrementAndGet()") {
    val r = Atomic(-3.toByte)
    assert(r.incrementAndGet === -2)
    assert(r.incrementAndGet === -1)
    assert(r.incrementAndGet === 0)
    val r2 = Atomic(127.toByte)
    assert(r2.incrementAndGet === -128)
    assert(r2.incrementAndGet === -127)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet())
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -24)
  }

  test("getAndIncrement()") {
    val r = Atomic(126.toByte)
    assert(r.getAndIncrement === 126)
    assert(r.getAndIncrement === 127)
    assert(r.getAndIncrement === -128)
    assert(r.get === -127)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement())
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -24)
  }

  test("decrementAndGet()") {
    val r = Atomic(2.toByte)
    assert(r.decrementAndGet === 1)
    assert(r.decrementAndGet === 0)
    assert(r.decrementAndGet === -1)
    assert(r.decrementAndGet === -2)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet())
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 24)
  }

  test("getAndDecrement()") {
    val r = Atomic(2.toByte)
    assert(r.getAndDecrement === 2)
    assert(r.getAndDecrement === 1)
    assert(r.getAndDecrement === 0)
    assert(r.getAndDecrement === -1)
    val r2 = Atomic(-127.toByte)
    assert(r2.getAndDecrement === -127)
    assert(r2.getAndDecrement === -128)
    assert(r2.getAndDecrement === 127)
    assert(r2.get === 126)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement())
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 24)
  }

  test("incrementAndGet(v)") {
    val r = Atomic(-3.toByte)
    assert(r.incrementAndGet(2.toByte) === -1)
    assert(r.incrementAndGet(2.toByte) === 1)
    val r2 = Atomic(127.toByte)
    assert(r2.incrementAndGet(2.toByte) === -127)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet(2.toByte))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -48)
  }

  test("getAndIncrement(v)") {
    val r = Atomic(126.toByte)
    assert(r.getAndIncrement(2.toByte) === 126)
    assert(r.getAndIncrement(2.toByte) === -128)
    assert(r.getAndIncrement(2.toByte) === -126)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement(2.toByte))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -48)
  }

  test("decrementAndGet(v)") {
    val r = Atomic(2.toByte)
    assert(r.decrementAndGet(2.toByte) === 0)
    assert(r.decrementAndGet(2.toByte) === -2)
    assert(r.decrementAndGet(2.toByte) === -4)
    assert(r.decrementAndGet(2.toByte) === -6)
    val r2 = Atomic(-127.toByte)
    assert(r2.decrementAndGet(2.toByte) === 127)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet(2.toByte))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 48)
  }

  test("getAndDecrement(v)") {
    val r = Atomic(2.toByte)
    assert(r.getAndDecrement(2.toByte) === 2)
    assert(r.getAndDecrement(2.toByte) === 0)
    assert(r.getAndDecrement(2.toByte) === -2)
    assert(r.getAndDecrement(2.toByte) === -4)
    val r2 = Atomic(-127.toByte)
    assert(r2.getAndDecrement(2.toByte) === -127)
    assert(r2.getAndDecrement(2.toByte) === 127)
    assert(r2.get === 125)

    val rs = Atomic(0.toByte)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement(2.toByte))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 48)
  }
}
