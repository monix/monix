package monifu.concurrent.atomic

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[JUnitRunner])
class AtomicDoubleTest extends FunSuite {
  test("get()") {
    assert(Atomic(2.0).get === 2.0)
    assert(Atomic(Double.MaxValue).get === Double.MaxValue)
    assert(Atomic(Double.MinPositiveValue).get === Double.MinPositiveValue)
    assert(Atomic(Double.MinValue).get === Double.MinValue)
    assert(Atomic(Double.NaN).get.isNaN)
    assert(Atomic(Double.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Double.PositiveInfinity).get.isPosInfinity)
  }

  test("set()") {
    val r = Atomic(0.0)
    r.set(2.0)
    assert(r.get === 2.0)
    r.set(Double.MaxValue)
    assert(r.get === Double.MaxValue)
    r.set(Double.MinPositiveValue)
    assert(r.get === Double.MinPositiveValue)
    r.set(Double.MinValue)
    assert(r.get === Double.MinValue)
    r.set(Double.NaN)
    assert(r.get.isNaN)
    r.set(Double.NegativeInfinity)
    assert(r.get.isNegInfinity)
    r.set(Double.PositiveInfinity)
    assert(r.get.isPosInfinity)
  }

  test("lazySet()") {
    val r = Atomic(0.0)
    r.lazySet(2.0)
    assert(r.get === 2.0)
    r.lazySet(Double.MaxValue)
    assert(r.get === Double.MaxValue)
    r.lazySet(Double.MinPositiveValue)
    assert(r.get === Double.MinPositiveValue)
    r.lazySet(Double.MinValue)
    assert(r.get === Double.MinValue)
    r.lazySet(Double.NaN)
    assert(r.get.isNaN)
    r.lazySet(Double.NegativeInfinity)
    assert(r.get.isNegInfinity)
    r.lazySet(Double.PositiveInfinity)
    assert(r.get.isPosInfinity)
  }

  test("compareAndSet()") {
    def boxInc(x: Double) = x.asInstanceOf[AnyRef]
    def unboxed(x: Double) = boxInc(x).asInstanceOf[Double]

    val r = Atomic(0.0)
    assert(r.compareAndSet(0.0, unboxed(1.0)))
    assert(!r.compareAndSet(0.0, unboxed(1.0)))
    assert(r.compareAndSet(1.0, Double.NaN))
    assert(r.compareAndSet(Double.NaN, Double.PositiveInfinity))
    assert(r.compareAndSet(unboxed(Double.PositiveInfinity), unboxed(2.0)))
    assert(r.get === 2.0)
  }

  test("weakCompareAndSet()") {
    def boxInc(x: Double) = x.asInstanceOf[AnyRef]
    def unboxed(x: Double) = boxInc(x).asInstanceOf[Double]

    val r = Atomic(0.0)
    assert(r.weakCompareAndSet(0.0, unboxed(1.0)))
    assert(!r.weakCompareAndSet(0.0, unboxed(1.0)))
    assert(r.weakCompareAndSet(1.0, Double.NaN))
    assert(r.weakCompareAndSet(Double.NaN, Double.PositiveInfinity))
    assert(r.weakCompareAndSet(unboxed(Double.PositiveInfinity), unboxed(2.0)))
    assert(r.get === 2.0)
  }

  test("getAndSet()") {
    val r = Atomic(0.0)
    assert(r.getAndSet(1.0) === 0.0)
    assert(r.getAndSet(2.0) === 1.0)
    assert(r.getAndSet(3.0) === 2.0)
    assert(r.getAndSet(4.0) === 3.0)
    assert(r.get === 4.0)
  }

  test("increment()") {
    val r = Atomic(0.0)
    r.increment
    assert(r.get === 1.0)
    r.increment
    assert(r.get === 2.0)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000.01)
  }

  test("decrement()") {
    val r = Atomic(0.0)
    r.decrement
    assert(r.get === -1.0)
    r.decrement
    assert(r.get === -2.0)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -999.99)
  }

  test("incrementAndGet()") {
    val r = Atomic(0.01)
    assert(r.incrementAndGet === 1.01)
    assert(r.incrementAndGet === 2.01)
    assert(r.incrementAndGet === 3.01)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000.01)
  }

  test("decrementAndGet()") {
    val r = Atomic(0.01)
    assert(r.decrementAndGet === -0.99)
    assert(r.decrementAndGet === -1.99)
    assert(r.decrementAndGet === -2.99)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -999.99)
  }

  test("getAndIncrement()") {
    val r = Atomic(0.01)
    assert(r.getAndIncrement === 0.01)
    assert(r.getAndIncrement === 1.01)
    assert(r.getAndIncrement === 2.01)
    assert(r.get === 3.01)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000.01)
  }

  test("getAndDecrement()") {
    val r = Atomic(0.01)
    assert(r.getAndDecrement === 0.01)
    assert(r.getAndDecrement === -0.99)
    assert(r.getAndDecrement === -1.99)
    assert(r.get === -2.99)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -999.99)
  }

  /////////////////////

  test("increment(v)") {
    val r = Atomic(0.0)
    r.add(2.0)
    assert(r.get === 2.0)
    r.add(2.0)
    assert(r.get === 4.0)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.add(2.0))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000.01)
  }

  test("decrement(v)") {
    val r = Atomic(0.0)
    r.subtract(2.0)
    assert(r.get === -2.0)
    r.subtract(2.0)
    assert(r.get === -4.0)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.subtract(2.0))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1999.99)
  }

  test("incrementAndGet(v)") {
    val r = Atomic(0.01)
    assert(r.addAndGet(2.0) === 2.01)
    assert(r.addAndGet(2.0) === 4.01)
    assert(r.addAndGet(2.0) === 6.01)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.addAndGet(2.0))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000.01)
  }

  test("decrementAndGet(v)") {
    val r = Atomic(0.01)
    assert(r.subtractAndGet(2.0) === -1.99)
    assert(r.subtractAndGet(2.0) === -3.99)
    assert(r.subtractAndGet(2.0) === -5.99)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.subtractAndGet(2.0))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1999.99)
  }

  test("getAndIncrement(v)") {
    val r = Atomic(0.01)
    assert(r.getAndAdd(2.0) === 0.01)
    assert(r.getAndIncrement(2) === 2.01)
    assert(r.getAndAdd(2.0) === 4.01)
    assert(r.get === 6.01)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndAdd(2.0))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000.01)
  }

  test("getAndDecrement(v)") {
    val r = Atomic(0.01)
    assert(r.getAndSubtract(2.0) === 0.01)
    assert(r.getAndSubtract(2.0) === -1.99)
    assert(r.getAndSubtract(2.0) === -3.99)
    assert(r.get === -5.99)

    val rs = Atomic(0.01)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndSubtract(2.0))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1999.99)
  }
}
