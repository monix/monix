package monifu.concurrent.atomic

import java.lang.Double.{longBitsToDouble, doubleToLongBits}
import scala.annotation.tailrec
import java.util.concurrent.atomic.{AtomicLong => JavaAtomicLong}

final class AtomicDouble private (ref: JavaAtomicLong) extends AtomicNumber[Double] {
  type Underlying = JavaAtomicLong
  def asJava = ref

  def get: Double = longBitsToDouble(ref.get)
  def set(update: Double) = ref.set(doubleToLongBits(update))
  def lazySet(update: Double) = ref.lazySet(doubleToLongBits(update))

  def compareAndSet(expect: Double, update: Double): Boolean =
    ref.compareAndSet(doubleToLongBits(expect), doubleToLongBits(update))

  def weakCompareAndSet(expect: Double, update: Double): Boolean =
    ref.weakCompareAndSet(doubleToLongBits(expect), doubleToLongBits(update))

  def getAndSet(update: Double): Double =
    longBitsToDouble(ref.getAndSet(doubleToLongBits(update)))

  def plusOp(a: Double, b: Double) = a + b
  def minusOp(a: Double, b: Double) = a - b
  def incrOp(a: Double, b: Int): Double = a + b
}

object AtomicDouble {
  def apply(initialValue: Double): AtomicDouble =
    new AtomicDouble(new JavaAtomicLong(doubleToLongBits(initialValue)))

  def apply(ref: JavaAtomicLong): AtomicDouble =
    new AtomicDouble(ref)
}