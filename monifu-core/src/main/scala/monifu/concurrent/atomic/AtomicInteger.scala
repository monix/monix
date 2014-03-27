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
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt = new AtomicInt(new AtomicInteger(initialValue))
}
