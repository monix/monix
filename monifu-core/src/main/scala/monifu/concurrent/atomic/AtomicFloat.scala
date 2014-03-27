package monifu.concurrent.atomic

import java.lang.Float.{intBitsToFloat, floatToIntBits}
import java.util.concurrent.atomic.AtomicInteger

final class AtomicFloat private (ref: AtomicInteger)
  extends AtomicNumber[Float] with BlockableAtomic[Float] with WeakAtomic[Float]
  with CommonOps[Float] with NumberCommonOps[Float] {

  def get: Float = intBitsToFloat(ref.get)

  def set(update: Float) = ref.set(floatToIntBits(update))

  def lazySet(update: Float) = ref.lazySet(floatToIntBits(update))

  def compareAndSet(expect: Float, update: Float): Boolean =
    ref.compareAndSet(floatToIntBits(expect), floatToIntBits(update))

  def weakCompareAndSet(expect: Float, update: Float): Boolean =
    ref.weakCompareAndSet(floatToIntBits(expect), floatToIntBits(update))

  def getAndSet(update: Float): Float =
    intBitsToFloat(ref.getAndSet(floatToIntBits(update)))

  def plusOp(a: Float, b: Float) = a + b
  def minusOp(a: Float, b: Float) = a - b
  def incrOp(a: Float, b: Int): Float = a + b
}

object AtomicFloat {
  def apply(initialValue: Float): AtomicFloat =
    new AtomicFloat(new AtomicInteger(floatToIntBits(initialValue)))
}
