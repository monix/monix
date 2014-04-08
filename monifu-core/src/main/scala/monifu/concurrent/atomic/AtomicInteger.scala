package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger

final class AtomicInt private (ref: AtomicInteger)
  extends AtomicNumber[Int] with BlockableAtomic[Int] with WeakAtomic[Int]
  with CommonOps[Int] with NumberCommonOps[Int] {

  def get: Int = ref.get()
  def set(update: Int) = ref.set(update)
  def lazySet(update: Int) = ref.lazySet(update)

  def compareAndSet(expect: Int, update: Int): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Int, update: Int): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Int): Int =
    ref.getAndSet(update)

  def plusOp(a: Int, b: Int) = a + b
  def minusOp(a: Int, b: Int) = a - b
  def incrOp(a: Int, b: Int): Int = a + b

  override def increment(): Unit =
    ref.incrementAndGet()

  override def decrement(): Unit =
    ref.decrementAndGet()

  override def incrementAndGet(): Int =
    ref.incrementAndGet()

  override def decrementAndGet(): Int =
    ref.decrementAndGet()

  override def decrementAndGet(v: Int): Int =
    ref.addAndGet(-v)

  override def getAndIncrement(): Int =
    ref.getAndIncrement

  override def getAndDecrement(): Int =
    ref.getAndDecrement

  override def getAndDecrement(v: Int): Int =
    ref.getAndAdd(-v)

  override def decrement(v: Int): Unit =
    ref.addAndGet(-v)
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt = new AtomicInt(new AtomicInteger(initialValue))
}
