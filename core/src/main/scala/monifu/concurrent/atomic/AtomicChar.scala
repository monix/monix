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

  def getAndSet(update: Char): Char =
    (ref.getAndSet(update) & mask).asInstanceOf[Char]

  def plusOp(a: Char, b: Char): Char = ((a + b) & mask).asInstanceOf[Char]
  def minusOp(a: Char, b: Char): Char = ((a - b) & mask).asInstanceOf[Char]
  def incrOp(a: Char, b: Int): Char = ((a + b) & mask).asInstanceOf[Char]

  private[this] val mask = 255 + 255 * 256
}

object AtomicChar {
  def apply(initialValue: Char): AtomicChar =
    new AtomicChar(new AtomicInteger(initialValue))
}
