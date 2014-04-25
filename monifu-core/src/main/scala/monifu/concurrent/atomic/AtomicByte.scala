package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicByte private (ref: AtomicInteger) extends AtomicNumber[Byte] {
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

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Byte): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Byte = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Byte): Byte = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Byte = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Byte): Byte = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Byte): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Byte): Byte = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Byte): Byte = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }


  @inline
  private[this] def plusOp(a: Byte, b: Byte): Byte = ((a + b) & mask).asInstanceOf[Byte]

  @inline
  private[this] def minusOp(a: Byte, b: Byte): Byte = ((a - b) & mask).asInstanceOf[Byte]

  @inline
  private[this] def incrOp(a: Byte, b: Int): Byte = ((a + b) & mask).asInstanceOf[Byte]

  private[this] val mask = 255
}

object AtomicByte {
  def apply(initialValue: Byte): AtomicByte =
    new AtomicByte(new AtomicInteger(initialValue))
}
