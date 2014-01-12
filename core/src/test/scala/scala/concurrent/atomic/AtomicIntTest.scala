package monifu.concurrent.atomic

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[JUnitRunner])
class AtomicIntTest extends FunSuite {
  test("set()") {
    val r = Atomic(11)
    assert(r.get === 11)

    r.set(100)
    assert(r.get === 100)
    r.lazySet(255)
    assert(r.get === 255)
  }

  test("getAndSet()") {
    val r = Atomic(100)
    assert(r.getAndSet(200) === 100)
    assert(r.getAndSet(0) === 200)
    assert(r.get === 0)
  }

  test("compareAndSet()") {
    val r = Atomic(0)

    assert(r.compareAndSet(0, 100), "CAS should succeed")
    assert(r.get === 100)
    assert(!r.compareAndSet(0, 200), "CAS should fail")
    assert(r.get === 100)
    assert(r.compareAndSet(100, 200), "CAS should succeed")
    assert(r.get === 200)
  }

  test("weakCompareAndSet()") {
    val r = Atomic(0)

    assert(r.weakCompareAndSet(0, 100), "CAS should succeed")
    assert(r.get === 100)
    assert(!r.weakCompareAndSet(0, 200), "CAS should fail")
    assert(r.get === 100)
    assert(r.weakCompareAndSet(100, 200), "CAS should succeed")
    assert(r.get === 200)
  }

  test("increment()") {
    val r = Atomic(0)
    assert(r.get === 0)

    r.increment
    assert(r.get === 1)
    r.increment
    assert(r.get === 2)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("increment(v)") {
    val r = Atomic(0)
    assert(r.get === 0)

    r.increment(2)
    assert(r.get === 2)

    r.increment(Int.MaxValue - 2)
    assert(r.get === Int.MaxValue)
    r.increment(2)
    assert(r.get === Int.MinValue + 1)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("decrement()") {
    val r = Atomic(1)
    assert(r.get === 1)

    r.decrement
    assert(r.get === 0)
    r.decrement
    assert(r.get === -1)
    r.decrement
    assert(r.get === -2)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000)
  }

  test("decrement(v)") {
    val r = Atomic(1)
    assert(r.get === 1)

    r.decrement(3)
    assert(r.get === -2)

    r.decrement(Int.MaxValue)
    assert(r.get === Int.MaxValue)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000)
  }


  test("incrementAndGet()") {
    val r = Atomic(Int.MaxValue - 2)
    assert(r.incrementAndGet === Int.MaxValue - 1)
    assert(r.incrementAndGet === Int.MaxValue)
    assert(r.incrementAndGet === Int.MinValue)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("getAndIncrement()") {
    val r = Atomic(126)
    assert(r.getAndIncrement === 126)
    assert(r.getAndIncrement === 127)
    assert(r.getAndIncrement === 128)
    assert(r.get === 129)

    val r2 = Atomic(Int.MaxValue)
    assert(r2.getAndIncrement === Int.MaxValue)
    assert(r2.get === Int.MinValue)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000)
  }

  test("decrementAndGet()") {
    val r = Atomic(Int.MinValue + 2)
    assert(r.decrementAndGet === Int.MinValue + 1)
    assert(r.decrementAndGet === Int.MinValue)
    assert(r.decrementAndGet === Int.MaxValue)
    assert(r.decrementAndGet === Int.MaxValue - 1)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000)
  }

  test("getAndDecrement()") {
    val r = Atomic(Int.MinValue + 2)
    assert(r.getAndDecrement === Int.MinValue + 2)
    assert(r.getAndDecrement === Int.MinValue + 1)
    assert(r.getAndDecrement === Int.MinValue)
    assert(r.getAndDecrement === Int.MaxValue)
    assert(r.getAndDecrement === Int.MaxValue - 1)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1000)
  }

  test("incrementAndGet(v)") {
    val r = Atomic(Int.MaxValue - 4)
    assert(r.incrementAndGet(2) === Int.MaxValue - 2)
    assert(r.incrementAndGet(2) === Int.MaxValue)
    assert(r.incrementAndGet(2) === Int.MinValue + 1)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("getAndIncrement(v)") {
    val r = Atomic(Int.MaxValue - 4)
    assert(r.getAndIncrement(2) === Int.MaxValue - 4)
    assert(r.getAndIncrement(2) === Int.MaxValue - 2)
    assert(r.getAndIncrement(2) === Int.MaxValue)
    assert(r.getAndIncrement(2) === Int.MinValue + 1)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000)
  }

  test("decrementAndGet(v)") {
    val r = Atomic(Int.MinValue + 2)
    assert(r.decrementAndGet(2) === Int.MinValue)
    assert(r.decrementAndGet(2) === Int.MaxValue - 1)
    assert(r.decrementAndGet(2) === Int.MaxValue - 3)
    assert(r.decrementAndGet(2) === Int.MaxValue - 5)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000)
  }

  test("getAndDecrement(v)") {
    val r = Atomic(Int.MinValue + 2)
    assert(r.getAndDecrement(2) === Int.MinValue + 2)
    assert(r.getAndDecrement(2) === Int.MinValue)
    assert(r.getAndDecrement(2) === Int.MaxValue - 1)
    assert(r.getAndDecrement(2) === Int.MaxValue - 3)
    assert(r.getAndDecrement(2) === Int.MaxValue - 5)

    val rs = Atomic(0)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement(2))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -2000)
  }
}
