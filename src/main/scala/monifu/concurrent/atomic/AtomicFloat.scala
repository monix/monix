package monifu.concurrent.atomic

import java.lang.Float.{intBitsToFloat, floatToIntBits}
import scala.annotation.tailrec
import java.util.concurrent.atomic.AtomicInteger


final class AtomicFloat protected (ref: AtomicInteger) extends AtomicNumber[Float] {
  type Underlying = AtomicInteger
  def asJava = ref

  def get: Float = intBitsToFloat(ref.get)
  def set(update: Float) = ref.set(floatToIntBits(update))
  def lazySet(update: Float) = ref.lazySet(floatToIntBits(update))

  def compareAndSet(expect: Float, update: Float): Boolean =
    ref.compareAndSet(floatToIntBits(expect), floatToIntBits(update))
  def weakCompareAndSet(expect: Float, update: Float): Boolean =
    ref.weakCompareAndSet(floatToIntBits(expect), floatToIntBits(update))
  def naturalCompareAndSet(expect: Float, update: Float): Boolean =
    ref.compareAndSet(floatToIntBits(expect), floatToIntBits(update))
  def naturalWeakCompareAndSet(expect: Float, update: Float): Boolean =
    ref.weakCompareAndSet(floatToIntBits(expect), floatToIntBits(update))
  def getAndSet(update: Float): Float =
    intBitsToFloat(ref.getAndSet(floatToIntBits(update)))

  @tailrec
  def transformAndExtract[U](cb: (Float) => (Float, U)): U = {
    val current = ref.get
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def weakTransformAndExtract[U](cb: (Float) => (Float, U)): U = {
    val current = ref.get
    val (update, extract) = cb(intBitsToFloat(current))
    if (!ref.weakCompareAndSet(current, floatToIntBits(update)))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Float) => Float): Float = {
    val current = ref.get
    val update = cb(intBitsToFloat(current))
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def weakTransformAndGet(cb: (Float) => Float): Float = {
    val current = ref.get
    val update = cb(intBitsToFloat(current))
    if (!ref.weakCompareAndSet(current, floatToIntBits(update)))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Float) => Float): Float = {
    val current = ref.get
    val currentFloat = intBitsToFloat(current)
    val update = cb(currentFloat)
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      getAndTransform(cb)
    else
      currentFloat
  }

  @tailrec
  def weakGetAndTransform(cb: (Float) => Float): Float = {
    val current = ref.get
    val currentFloat = intBitsToFloat(current)
    val update = cb(currentFloat)
    if (!ref.weakCompareAndSet(current, floatToIntBits(update)))
      weakGetAndTransform(cb)
    else
      currentFloat
  }

  @tailrec
  def transform(cb: (Float) => Float) {
    val current = ref.get
    val update = cb(intBitsToFloat(current))
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      transform(cb)
  }

  @tailrec
  def weakTransform(cb: (Float) => Float) {
    val current = ref.get
    val update = cb(intBitsToFloat(current))
    if (!ref.weakCompareAndSet(current, floatToIntBits(update)))
      weakTransform(cb)
  }

  @tailrec
  def add(v: Float) {
    val current = ref.get
    val update = intBitsToFloat(current) + v
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      add(v)
  }

  @tailrec
  def addAndGet(v: Float): Float = {
    val current = ref.get
    val update = intBitsToFloat(current) + v
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndAdd(v: Float): Float = {
    val current = ref.get
    val currentFloat = intBitsToFloat(current)
    val update = currentFloat + v
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      getAndAdd(v)
    else
      currentFloat
  }

  @tailrec
  def increment(v: Int) {
    val current = ref.get
    val update = intBitsToFloat(current) + v
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Float = {
    val current = ref.get
    val update = intBitsToFloat(current) + v
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Float = {
    val current = ref.get
    val currentFloat = intBitsToFloat(current)
    val update = currentFloat + v
    if (!ref.compareAndSet(current, floatToIntBits(update)))
      getAndIncrement(v)
    else
      currentFloat
  }

  def increment() = increment(1)
  def decrement() = increment(-1)
  def decrement(v: Int) = increment(-v)
  def subtract(v: Float) = add(-v)

  def incrementAndGet() = incrementAndGet(1)
  def decrementAndGet() = incrementAndGet(-1)
  def decrementAndGet(v: Int) = incrementAndGet(-v)
  def subtractAndGet(v: Float) = addAndGet(-v)

  def getAndIncrement(): Float = getAndIncrement(1)
  def getAndDecrement(): Float = getAndIncrement(-1)

  def getAndDecrement(v: Int): Float = getAndIncrement(-v)
  def getAndSubtract(v: Float): Float = getAndAdd(-v)
}

object AtomicFloat {
  def apply(initialValue: Float): AtomicFloat =
    new AtomicFloat(new AtomicInteger(floatToIntBits(initialValue)))

  def apply(ref: AtomicInteger): AtomicFloat =
    new AtomicFloat(ref)
}
