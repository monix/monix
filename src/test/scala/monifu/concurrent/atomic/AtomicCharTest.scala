package monifu.concurrent.atomic

import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class AtomicCharTest extends FunSuite {
  test("set()") {
    val r = Atomic(11.toChar)
    assert(r.get === 11)

    r.set(100)
    assert(r.get === 100)
    r.lazySet(255)
    assert(r.get === 255)
  }

  test("getAndSet()") {
    val r = Atomic(100.toChar)
    assert(r.getAndSet(200.toChar) === 100.toChar)
    assert(r.getAndSet(0) === 200.toChar)
    assert(r.get === 0)
  }

  test("compareAndSet()") {
    val r = Atomic(0.toChar)

    assert(r.compareAndSet(0.toChar, 100.toChar), "CAS should succeed")
    assert(r.get === 100.toChar)
    assert(!r.compareAndSet(0.toChar, 200.toChar), "CAS should fail")
    assert(r.get === 100.toChar)
    assert(r.compareAndSet(100.toChar, 200.toChar), "CAS should succeed")
    assert(r.get === 200.toChar)
  }

  test("weakCompareAndSet()") {
    val r = Atomic(0.toChar)

    assert(r.weakCompareAndSet(0.toChar, 100.toChar), "CAS should succeed")
    assert(r.get === 100.toChar)
    assert(!r.weakCompareAndSet(0.toChar, 200.toChar), "CAS should fail")
    assert(r.get === 100.toChar)
    assert(r.weakCompareAndSet(100.toChar, 200.toChar), "CAS should succeed")
    assert(r.get === 200.toChar)
  }

  test("increment()") {
    val r = Atomic(0.toChar)
    assert(r.get === 0)

    r.increment
    assert(r.get === 1)
    r.increment
    assert(r.get === 2)

    for (i <- 0 until 253 + 255 * 256)
      r.increment

    assert(r.get === Char.MaxValue)
    r.increment
    assert(r.get === 0)
    r.increment
    assert(r.get === 1)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("increment(v)") {
    val r = Atomic(0.toChar)
    assert(r.get === 0)

    r.increment(2.toChar)
    assert(r.get === 2)

    r.increment((Char.MaxValue - 2).toChar)
    assert(r.get === Char.MaxValue)
    r.increment(2.toChar)
    assert(r.get === 1)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment(2.toChar))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("decrement()") {
    val r = Atomic(1.toChar)
    assert(r.get === 1)

    r.decrement
    assert(r.get === 0)
    r.decrement
    assert(r.get === Char.MaxValue)
    r.decrement
    assert(r.get === Char.MaxValue - 1)
    for (i <- 0 until 254 + 255 * 256)
      r.decrement
    assert(r.get === 0)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === Char.MaxValue - 999)
  }

  test("decrement(v)") {
    val r = Atomic(1.toChar)
    assert(r.get === 1)

    r.decrement(3.toChar)
    assert(r.get === Char.MaxValue - 1)

    r.decrement((254 + 255 * 256).toChar)
    assert(r.get === 0)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement(2.toChar))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get.toInt === Char.MaxValue - 1999)
  }


  test("incrementAndGet()") {
    val r = Atomic((Char.MaxValue - 2).toChar)
    assert(r.incrementAndGet === Char.MaxValue - 1)
    assert(r.incrementAndGet === Char.MaxValue)
    assert(r.incrementAndGet === 0)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("getAndIncrement()") {
    val r = Atomic(126.toChar)
    assert(r.getAndIncrement === 126)
    assert(r.getAndIncrement === 127)
    assert(r.getAndIncrement === 128)
    assert(r.get === 129)

    val r2 = Atomic(Char.MaxValue)
    assert(r2.getAndIncrement === Char.MaxValue)
    assert(r2.get === 0)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("decrementAndGet()") {
    val r = Atomic(2.toChar)
    assert(r.decrementAndGet === 1)
    assert(r.decrementAndGet === 0)
    assert(r.decrementAndGet === Char.MaxValue)
    assert(r.decrementAndGet === Char.MaxValue - 1)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === Char.MaxValue - 999)
  }

  test("getAndDecrement()") {
    val r = Atomic(2.toChar)
    assert(r.getAndDecrement === 2)
    assert(r.getAndDecrement === 1)
    assert(r.getAndDecrement === 0)
    assert(r.getAndDecrement === Char.MaxValue)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === Char.MaxValue - 999)
  }

  test("incrementAndGet(v)") {
    val r = Atomic((Char.MaxValue - 4).toChar)
    assert(r.incrementAndGet(2.toChar) === Char.MaxValue - 2)
    assert(r.incrementAndGet(2.toChar) === Char.MaxValue)
    assert(r.incrementAndGet(2.toChar) === 1)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet(2.toChar))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("getAndIncrement(v)") {
    val r = Atomic((Char.MaxValue - 4).toChar)
    assert(r.getAndIncrement(2.toChar) === Char.MaxValue - 4)
    assert(r.getAndIncrement(2.toChar) === Char.MaxValue - 2)
    assert(r.getAndIncrement(2.toChar) === Char.MaxValue)
    assert(r.getAndIncrement(2.toChar) === 1)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement(2.toChar))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("decrementAndGet(v)") {
    val r = Atomic(2.toChar)
    assert(r.decrementAndGet(2.toChar) === 0)
    assert(r.decrementAndGet(2.toChar) === Char.MaxValue - 1)
    assert(r.decrementAndGet(2.toChar) === Char.MaxValue - 3)
    assert(r.decrementAndGet(2.toChar) === Char.MaxValue - 5)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet(2.toChar))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === Char.MaxValue - 1999)
  }

  test("getAndDecrement(v)") {
    val r = Atomic(2.toChar)
    assert(r.getAndDecrement(2.toChar) === 2)
    assert(r.getAndDecrement(2.toChar) === 0)
    assert(r.getAndDecrement(2.toChar) === Char.MaxValue - 1)
    assert(r.getAndDecrement(2.toChar) === Char.MaxValue - 3)
    assert(r.getAndDecrement(2.toChar) === Char.MaxValue - 5)

    val rs = Atomic(0.toChar)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement(2.toChar))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === Char.MaxValue - 1999)
  }
}
