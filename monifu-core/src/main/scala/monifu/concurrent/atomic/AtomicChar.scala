package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicChar private (ref: AtomicInteger) extends AtomicNumber[Char] {
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

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Char): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Char = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Char): Char = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Char = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Char): Char = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Char): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Char): Char = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Char): Char = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @inline private[this] def plusOp(a: Char, b: Char): Char = ((a + b) & mask).asInstanceOf[Char]
  @inline private[this] def minusOp(a: Char, b: Char): Char = ((a - b) & mask).asInstanceOf[Char]
  @inline private[this] def incrOp(a: Char, b: Int): Char = ((a + b) & mask).asInstanceOf[Char]

  private[this] val mask = 255 + 255 * 256
}

object AtomicChar {
  def apply(initialValue: Char): AtomicChar =
    new AtomicChar(new AtomicInteger(initialValue))
}
