package monifu.concurrent.atomic

import java.lang.Double.{longBitsToDouble, doubleToLongBits}
import java.util.concurrent.atomic.{AtomicLong => JavaAtomicLong}
import scala.annotation.tailrec

final class AtomicDouble private (ref: JavaAtomicLong) extends AtomicNumber[Double] {
  def get: Double = longBitsToDouble(ref.get)
  def set(update: Double) = ref.set(doubleToLongBits(update))
  def lazySet(update: Double) = ref.lazySet(doubleToLongBits(update))

  def compareAndSet(expect: Double, update: Double): Boolean =
    ref.compareAndSet(doubleToLongBits(expect), doubleToLongBits(update))

  def weakCompareAndSet(expect: Double, update: Double): Boolean =
    ref.weakCompareAndSet(doubleToLongBits(expect), doubleToLongBits(update))

  def getAndSet(update: Double): Double =
    longBitsToDouble(ref.getAndSet(doubleToLongBits(update)))

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Double): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Double = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Double): Double = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Double = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Double): Double = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Double): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Double): Double = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Double): Double = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }
  
  @inline private[this] def plusOp(a: Double, b: Double) = a + b
  @inline private[this] def minusOp(a: Double, b: Double) = a - b
  @inline private[this] def incrOp(a: Double, b: Int): Double = a + b
}

object AtomicDouble {
  def apply(initialValue: Double): AtomicDouble =
    new AtomicDouble(new JavaAtomicLong(doubleToLongBits(initialValue)))
}