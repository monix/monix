package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger

final class AtomicByte private (ref: AtomicInteger)
  extends AtomicNumber[Byte] with CommonOps[Byte] with NumberCommonOps[Byte] {

  def get: Byte =
    (ref.get() & mask).asInstanceOf[Byte]

  def set(update: Byte) = ref.set(update)

  def lazySet(update: Byte) = ref.lazySet(update)

  def compareAndSet(expect: Byte, update: Byte): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Byte, update: Byte): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Byte): Byte =
    (ref.getAndSet(update) & mask).asInstanceOf[Byte]

  def plusOp(a: Byte, b: Byte): Byte = ((a + b) & mask).asInstanceOf[Byte]
  def minusOp(a: Byte, b: Byte): Byte = ((a - b) & mask).asInstanceOf[Byte]
  def incrOp(a: Byte, b: Int): Byte = ((a + b) & mask).asInstanceOf[Byte]

  private[this] val mask = 255
}

object AtomicByte {
  def apply(initialValue: Byte): AtomicByte =
    new AtomicByte(new AtomicInteger(initialValue))
}
