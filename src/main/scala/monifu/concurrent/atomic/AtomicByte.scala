package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicByte private (ref: AtomicInteger) extends AtomicNumber[Byte] {
  final type Underlying = AtomicInteger
  final def asJava = ref

  final def get: Byte =
    (ref.get() & mask).asInstanceOf[Byte]

  final def set(update: Byte) = ref.set(update)

  final def lazySet(update: Byte) = ref.lazySet(update)

  final def compareAndSet(expect: Byte, update: Byte): Boolean =
    ref.compareAndSet(expect, update)
  def weakCompareAndSet(expect: Byte, update: Byte): Boolean =
    ref.weakCompareAndSet(expect, update)
  def naturalCompareAndSet(expect: Byte, update: Byte): Boolean =
    ref.compareAndSet(expect, update)
  def naturalWeakCompareAndSet(expect: Byte, update: Byte): Boolean =
    ref.weakCompareAndSet(expect, update)
  def getAndSet(update: Byte): Byte =
    (ref.getAndSet(update) & mask).asInstanceOf[Byte]

  @tailrec
  final def transformAndExtract[U](cb: (Byte) => (Byte, U)): U = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def weakTransformAndExtract[U](cb: (Byte) => (Byte, U)): U = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val (update, extract) = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (Byte) => Byte): Byte = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def weakTransformAndGet(cb: (Byte) => Byte): Byte = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (Byte) => Byte): Byte = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def weakGetAndTransform(cb: (Byte) => Byte): Byte = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (Byte) => Byte) {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  final def weakTransform(cb: (Byte) => Byte) {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransform(cb)
  }

  @tailrec
  final def incrementAndGet(v: Int): Byte = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = ((current + v) & mask).asInstanceOf[Byte]
    if (!ref.compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  final def increment(v: Int) {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = ((current + v) & mask).asInstanceOf[Byte]
    if (!ref.compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  final def getAndIncrement(v: Int): Byte = {
    val current = (ref.get() & mask).asInstanceOf[Byte]
    val update = ((current + v) & mask).asInstanceOf[Byte]
    if (!ref.compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  final def increment() = increment(1)
  final def add(v: Byte) = increment(v)
  final def incrementAndGet(): Byte = incrementAndGet(1)
  final def addAndGet(v: Byte): Byte = incrementAndGet(v)
  final def getAndIncrement(): Byte = getAndIncrement(1)
  final def getAndAdd(v: Byte): Byte = getAndIncrement(v)

  final def decrement() = increment(-1)
  final def decrement(v: Int) = increment(-v)
  final def subtract(v: Byte) = increment(-v)

  final def decrementAndGet(): Byte = incrementAndGet(-1)
  final def decrementAndGet(v: Int): Byte = incrementAndGet(-v)
  final def subtractAndGet(v: Byte): Byte = incrementAndGet(-v)

  final def getAndDecrement(): Byte = getAndIncrement(-1)
  final def getAndDecrement(v: Int): Byte = getAndIncrement(-v)
  final def getAndSubtract(v: Byte): Byte = getAndIncrement(-v)

  private[this] val mask = 255
}

object AtomicByte {
  def apply(initialValue: Byte): AtomicByte =
    new AtomicByte(new AtomicInteger(initialValue))
}
