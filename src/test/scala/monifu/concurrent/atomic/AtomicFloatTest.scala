package monifu.concurrent.atomic

import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class AtomicFloatTest extends FunSuite {
  test("get()") {
    assert(Atomic(2.0f).get === 2.0f)
    assert(Atomic(Float.MaxValue).get === Float.MaxValue)
    assert(Atomic(Float.MinPositiveValue).get === Float.MinPositiveValue)
    assert(Atomic(Float.MinValue).get === Float.MinValue)
    assert(Atomic(Float.NaN).get.isNaN)
    assert(Atomic(Float.NegativeInfinity).get.isNegInfinity)
    assert(Atomic(Float.PositiveInfinity).get.isPosInfinity)
  }

  test("set()") {
    val r = Atomic(0.0f)
    r.set(2.0f)
    assert(r.get === 2.0f)
    r.set(Float.MaxValue)
    assert(r.get === Float.MaxValue)
    r.set(Float.MinPositiveValue)
    assert(r.get === Float.MinPositiveValue)
    r.set(Float.MinValue)
    assert(r.get === Float.MinValue)
    r.set(Float.NaN)
    assert(r.get.isNaN)
    r.set(Float.NegativeInfinity)
    assert(r.get.isNegInfinity)
    r.set(Float.PositiveInfinity)
    assert(r.get.isPosInfinity)
  }

  test("lazySet()") {
    val r = Atomic(0.0f)
    r.lazySet(2.0f)
    assert(r.get === 2.0f)
    r.lazySet(Float.MaxValue)
    assert(r.get === Float.MaxValue)
    r.lazySet(Float.MinPositiveValue)
    assert(r.get === Float.MinPositiveValue)
    r.lazySet(Float.MinValue)
    assert(r.get === Float.MinValue)
    r.lazySet(Float.NaN)
    assert(r.get.isNaN)
    r.lazySet(Float.NegativeInfinity)
    assert(r.get.isNegInfinity)
    r.lazySet(Float.PositiveInfinity)
    assert(r.get.isPosInfinity)
  }

  test("compareAndSet()") {
    def boxInc(x: Float) = x.asInstanceOf[AnyRef]
    def unboxed(x: Float) = boxInc(x).asInstanceOf[Float]

    val r = Atomic(0.0f)
    assert(r.compareAndSet(0.0f, unboxed(1.0f)))
    assert(!r.compareAndSet(0.0f, unboxed(1.0f)))
    assert(r.compareAndSet(1.0f, Float.NaN))
    assert(r.compareAndSet(Float.NaN, Float.PositiveInfinity))
    assert(r.compareAndSet(unboxed(Float.PositiveInfinity), unboxed(2.0f)))
    assert(r.get === 2.0f)
  }

  test("weakCompareAndSet()") {
    def boxInc(x: Float) = x.asInstanceOf[AnyRef]
    def unboxed(x: Float) = boxInc(x).asInstanceOf[Float]

    val r = Atomic(0.0f)
    assert(r.weakCompareAndSet(0.0f, unboxed(1.0f)))
    assert(!r.weakCompareAndSet(0.0f, unboxed(1.0f)))
    assert(r.weakCompareAndSet(1.0f, Float.NaN))
    assert(r.weakCompareAndSet(Float.NaN, Float.PositiveInfinity))
    assert(r.weakCompareAndSet(unboxed(Float.PositiveInfinity), unboxed(2.0f)))
    assert(r.get === 2.0f)
  }

  test("getAndSet()") {
    val r = Atomic(0.0f)
    assert(r.getAndSet(1.0f) === 0.0f)
    assert(r.getAndSet(2.0f) === 1.0f)
    assert(r.getAndSet(3.0f) === 2.0f)
    assert(r.getAndSet(4.0f) === 3.0f)
    assert(r.get === 4.0f)
  }

  test("increment()") {
    val r = Atomic(0.0f)
    r.increment
    assert(r.get === 1.0f)
    r.increment
    assert(r.get === 2.0f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.increment)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000.01f)
  }

  test("decrement()") {
    val r = Atomic(0.0f)
    r.decrement
    assert(r.get === -1.0f)
    r.decrement
    assert(r.get === -2.0f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -999.99f)
  }

  test("incrementAndGet()") {
    val r = Atomic(0.01f)
    assert(r.incrementAndGet === 1.01f)
    assert(r.incrementAndGet === 2.01f)
    assert(r.incrementAndGet === 3.01f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.incrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000.01f)
  }

  test("subtractAndGet()") {
    val r = Atomic(0.01f)
    assert(r.decrementAndGet === -0.99f)
    assert(r.decrementAndGet === -1.99f)
    assert(r.decrementAndGet === -2.99f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.decrementAndGet)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -999.99f)
  }

  test("getAndAdd()") {
    val r = Atomic(0.01f)
    assert(r.getAndIncrement === 0.01f)
    assert(r.getAndIncrement === 1.01f)
    assert(r.getAndIncrement === 2.01f)
    assert(r.get === 3.01f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndIncrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 1000.01f)
  }

  test("getAndSubtract()") {
    val r = Atomic(0.01f)
    assert(r.getAndDecrement === 0.01f) 
    assert(r.getAndDecrement === -0.99f)
    assert(r.getAndDecrement === -1.99f)
    assert(r.get === -2.99f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndDecrement)
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -999.99f)
  }

  test("increment(v)") {
    val r = Atomic(0.0f)
    r.add(2.0f)
    assert(r.get === 2.0f)
    r.add(2.0f)
    assert(r.get === 4.0f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.add(2.0f))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000.01f)
  }

  test("decrement(v)") {
    val r = Atomic(0.0f)
    r.subtract(2.0f)
    assert(r.get === -2.0f)
    r.subtract(2.0f)
    assert(r.get === -4.0f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.subtract(2.0f))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1999.99f)
  }

  test("addAndGet(v)") {
    val r = Atomic(0.01f)
    assert(r.addAndGet(2.0f) === 2.01f)
    assert(r.addAndGet(2.0f) === 4.01f)
    assert(r.addAndGet(2.0f) === 6.01f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.addAndGet(2.0f))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000.01f)
  }

  test("subtractAndGet(v)") {
    val r = Atomic(0.01f)
    assert(r.subtractAndGet(2.0f) === -1.99f)
    assert(r.subtractAndGet(2.0f) === -3.99f)
    assert(r.subtractAndGet(2.0f) === -5.99f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.subtractAndGet(2.0f))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1999.99f)
  }

  test("getAndAdd(v)") {
    val r = Atomic(0.01f)
    assert(r.getAndAdd(2.0f) === 0.01f)
    assert(r.getAndAdd(2.0f) === 2.01f)
    assert(r.getAndAdd(2.0f) === 4.01f)
    assert(r.get === 6.01f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndAdd(2.0f))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === 2000.01f)
  }

  test("getAndSubtract(v)") {
    val r = Atomic(0.01f)
    assert(r.getAndSubtract(2.0f) === 0.01f)
    assert(r.getAndSubtract(2.0f) === -1.99f)
    assert(r.getAndSubtract(2.0f) === -3.99f)
    assert(r.get === -5.99f)

    val rs = Atomic(0.01f)
    val fs = for (i <- 0 until 1000) yield Future(rs.getAndSubtract(2.0f))
    Await.result(Future.sequence(fs), Duration.Inf)
    assert(rs.get === -1999.99f)
  }
}
