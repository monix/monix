package monifu.concurrent.atomic

import java.lang.Float.{intBitsToFloat, floatToIntBits}
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicFloat private (ref: AtomicInteger) extends AtomicNumber[Float] {
  def get: Float = intBitsToFloat(ref.get)

  def set(update: Float) = ref.set(floatToIntBits(update))

  def lazySet(update: Float) = ref.lazySet(floatToIntBits(update))

  def compareAndSet(expect: Float, update: Float): Boolean =
    ref.compareAndSet(floatToIntBits(expect), floatToIntBits(update))

  def weakCompareAndSet(expect: Float, update: Float): Boolean =
    ref.weakCompareAndSet(floatToIntBits(expect), floatToIntBits(update))

  def getAndSet(update: Float): Float =
    intBitsToFloat(ref.getAndSet(floatToIntBits(update)))

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Float): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Float = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Float): Float = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Float = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Float): Float = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Float): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Float): Float = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Float): Float = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }
  
  @inline private[this] def plusOp(a: Float, b: Float) = a + b
  @inline private[this] def minusOp(a: Float, b: Float) = a - b
  @inline private[this] def incrOp(a: Float, b: Int): Float = a + b
}

object AtomicFloat {
  def apply(initialValue: Float): AtomicFloat =
    new AtomicFloat(new AtomicInteger(floatToIntBits(initialValue)))
}
