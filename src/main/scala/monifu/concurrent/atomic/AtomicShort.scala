package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicShort private (ref: AtomicInteger) extends AtomicNumber[Short] {
  type Underlying = AtomicInteger
  def asJava = ref

  def get: Short =
    (ref.get() & mask).asInstanceOf[Short]

  def set(update: Short) = ref.set(update)
  def lazySet(update: Short) = ref.lazySet(update)

  def compareAndSet(expect: Short, update: Short): Boolean =
    ref.compareAndSet(expect, update)
  def weakCompareAndSet(expect: Short, update: Short): Boolean =
    ref.weakCompareAndSet(expect, update)
  def naturalCompareAndSet(expect: Short, update: Short): Boolean =
    ref.compareAndSet(expect, update)
  def naturalWeakCompareAndSet(expect: Short, update: Short): Boolean =
    ref.weakCompareAndSet(expect, update)
  def getAndSet(update: Short): Short =
    (ref.getAndSet(update) & mask).asInstanceOf[Short]

  @tailrec
  def transformAndExtract[U](cb: (Short) => (Short, U)): U = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def weakTransformAndExtract[U](cb: (Short) => (Short, U)): U = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val (update, extract) = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Short) => Short): Short = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def weakTransformAndGet(cb: (Short) => Short): Short = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Short) => Short): Short = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def weakGetAndTransform(cb: (Short) => Short): Short = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Short) => Short) {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def weakTransform(cb: (Short) => Short) {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransform(cb)
  }

  @tailrec
  def incrementAndGet(v: Int): Short = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = ((current + v) & mask).asInstanceOf[Short]
    if (!ref.compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def increment(v: Int) {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = ((current + v) & mask).asInstanceOf[Short]
    if (!ref.compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def getAndIncrement(v: Int): Short = {
    val current = (ref.get() & mask).asInstanceOf[Short]
    val update = ((current + v) & mask).asInstanceOf[Short]
    if (!ref.compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  def increment() = increment(1)
  def add(v: Short) = increment(v)
  def incrementAndGet(): Short = incrementAndGet(1)
  def addAndGet(v: Short): Short = incrementAndGet(v)
  def getAndIncrement(): Short = getAndIncrement(1)
  def getAndAdd(v: Short): Short = getAndIncrement(v)

  def decrement() = increment(-1)
  def decrement(v: Int) = increment(-v)
  def subtract(v: Short) = increment(-v)

  def decrementAndGet(): Short = incrementAndGet(-1)
  def decrementAndGet(v: Int): Short = incrementAndGet(-v)
  def subtractAndGet(v: Short): Short = incrementAndGet(-v)

  def getAndDecrement(): Short = getAndIncrement(-1)
  def getAndDecrement(v: Int): Short = getAndIncrement(-v)
  def getAndSubtract(v: Short): Short = getAndIncrement(-v)

  private[this] val mask = 255 + 255 * 256
}

object AtomicShort {
  def apply(initialValue: Short): AtomicShort =
    new AtomicShort(new AtomicInteger(initialValue))
}
