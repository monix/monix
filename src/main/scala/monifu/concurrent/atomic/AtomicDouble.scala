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
  def naturalCompareAndSet(expect: Double, update: Double): Boolean =
    ref.compareAndSet(doubleToLongBits(expect), doubleToLongBits(update))
  def naturalWeakCompareAndSet(expect: Double, update: Double): Boolean =
    ref.weakCompareAndSet(doubleToLongBits(expect), doubleToLongBits(update))
  def getAndSet(update: Double): Double =
    longBitsToDouble(ref.getAndSet(doubleToLongBits(update)))

  @tailrec
  def transformAndExtract[U](cb: (Double) => (Double, U)): U = {
    val current = ref.get
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def weakTransformAndExtract[U](cb: (Double) => (Double, U)): U = {
    val current = ref.get
    val (update, extract) = cb(longBitsToDouble(current))
    if (!ref.weakCompareAndSet(current, doubleToLongBits(update)))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Double) => Double): Double = {
    val current = ref.get
    val update = cb(longBitsToDouble(current))
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def weakTransformAndGet(cb: (Double) => Double): Double = {
    val current = ref.get
    val update = cb(longBitsToDouble(current))
    if (!ref.weakCompareAndSet(current, doubleToLongBits(update)))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Double) => Double): Double = {
    val current = ref.get
    val currentDouble = longBitsToDouble(current)
    val update = cb(currentDouble)
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      getAndTransform(cb)
    else
      currentDouble
  }

  @tailrec
  def weakGetAndTransform(cb: (Double) => Double): Double = {
    val current = ref.get
    val currentDouble = longBitsToDouble(current)
    val update = cb(currentDouble)
    if (!ref.weakCompareAndSet(current, doubleToLongBits(update)))
      weakGetAndTransform(cb)
    else
      currentDouble
  }

  @tailrec
  def transform(cb: (Double) => Double) {
    val current = ref.get
    val update = cb(longBitsToDouble(current))
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      transform(cb)
  }

  @tailrec
  def weakTransform(cb: (Double) => Double) {
    val current = ref.get
    val update = cb(longBitsToDouble(current))
    if (!ref.weakCompareAndSet(current, doubleToLongBits(update)))
      weakTransform(cb)
  }

  @tailrec
  def add(v: Double) {
    val current = ref.get
    val update = longBitsToDouble(current) + v
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      add(v)
  }

  @tailrec
  def addAndGet(v: Double): Double = {
    val current = ref.get
    val update = longBitsToDouble(current) + v
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndAdd(v: Double): Double = {
    val current = ref.get
    val currentDouble = longBitsToDouble(current)
    val update = currentDouble + v
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      getAndAdd(v)
    else
      currentDouble
  }

  @tailrec
  def increment(v: Int) {
    val current = ref.get
    val update = longBitsToDouble(current) + v
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Double = {
    val current = ref.get
    val update = longBitsToDouble(current) + v
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Double = {
    val current = ref.get
    val currentDouble = longBitsToDouble(current)
    val update = currentDouble + v
    if (!ref.compareAndSet(current, doubleToLongBits(update)))
      getAndIncrement(v)
    else
      currentDouble
  }

  def increment() = increment(1)
  def decrement() = increment(-1)
  def decrement(v: Int) = increment(-v)
  def subtract(v: Double) = add(-v)

  def incrementAndGet() = incrementAndGet(1)
  def decrementAndGet() = incrementAndGet(-1)
  def decrementAndGet(v: Int) = incrementAndGet(-v)
  def subtractAndGet(v: Double) = addAndGet(-v)

  def getAndIncrement(): Double = getAndIncrement(1)
  def getAndDecrement(): Double = getAndIncrement(-1)
  def getAndDecrement(v: Int): Double = getAndIncrement(-v)
  def getAndSubtract(v: Double): Double = getAndAdd(-v)
}

object AtomicDouble {
  def apply(initialValue: Double): AtomicDouble =
    new AtomicDouble(new JavaAtomicLong(doubleToLongBits(initialValue)))

  def apply(ref: JavaAtomicLong): AtomicDouble =
    new AtomicDouble(ref)
}