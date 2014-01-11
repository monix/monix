package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicChar private (ref: AtomicInteger) extends AtomicNumber[Char] {
  type Underlying = AtomicInteger
  def asJava = ref

  def get: Char =
    (ref.get() & mask).asInstanceOf[Char]

  def set(update: Char) = ref.set(update)
  def lazySet(update: Char) = ref.lazySet(update)

  def compareAndSet(expect: Char, update: Char): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Char, update: Char): Boolean =
    ref.weakCompareAndSet(expect, update)

  def naturalCompareAndSet(expect: Char, update: Char): Boolean =
    ref.compareAndSet(expect, update)

  def naturalWeakCompareAndSet(expect: Char, update: Char): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Char): Char =
    (ref.getAndSet(update) & mask).asInstanceOf[Char]

  @tailrec
  def transformAndExtract[U](cb: (Char) => (Char, U)): U = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def weakTransformAndExtract[U](cb: (Char) => (Char, U)): U = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val (update, extract) = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Char) => Char): Char = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def weakTransformAndGet(cb: (Char) => Char): Char = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Char) => Char): Char = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def weakGetAndTransform(cb: (Char) => Char): Char = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Char) => Char) {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def weakTransform(cb: (Char) => Char) {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransform(cb)
  }

  @tailrec
  def incrementAndGet(v: Int): Char = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = ((current + v) & mask).asInstanceOf[Char]
    if (!ref.compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def increment(v: Int) {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = ((current + v) & mask).asInstanceOf[Char]
    if (!ref.compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def getAndIncrement(v: Int): Char = {
    val current = (ref.get() & mask).asInstanceOf[Char]
    val update = ((current + v) & mask).asInstanceOf[Char]
    if (!ref.compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  def increment(): Unit = increment(1)
  def add(v: Char) = increment(v)
  def incrementAndGet(): Char = incrementAndGet(1)
  def addAndGet(v: Char): Char = incrementAndGet(v)
  def getAndIncrement(): Char = getAndIncrement(1)
  def getAndAdd(v: Char): Char = getAndIncrement(v)

  def decrement() = increment(-1)
  def decrement(v: Int) = increment(-v)
  def subtract(v: Char) = increment(-v)

  def decrementAndGet(): Char = incrementAndGet(-1)
  def decrementAndGet(v: Int): Char = incrementAndGet(-v)
  def subtractAndGet(v: Char): Char = incrementAndGet(-v)

  def getAndDecrement(): Char = getAndIncrement(-1)
  def getAndDecrement(v: Int): Char = getAndIncrement(-v)
  def getAndSubtract(v: Char): Char = getAndIncrement(-v)

  private[this] val mask = 255 + 255 * 256
}

object AtomicChar {
  def apply(initialValue: Char): AtomicChar =
    new AtomicChar(new AtomicInteger(initialValue))
}
