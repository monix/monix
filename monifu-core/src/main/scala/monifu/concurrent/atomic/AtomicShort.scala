package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicShort private (ref: AtomicInteger) extends AtomicNumber[Short] {
  def get: Short =
    (ref.get() & mask).asInstanceOf[Short]

  def set(update: Short) = ref.set(update)

  def lazySet(update: Short) = ref.lazySet(update)

  def compareAndSet(expect: Short, update: Short): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Short, update: Short): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Short): Short =
    (ref.getAndSet(update) & mask).asInstanceOf[Short]

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Short): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Short = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Short): Short = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Short = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Short): Short = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Short): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Short): Short = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Short): Short = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }
  
  @inline private[this] def plusOp(a: Short, b: Short): Short =
    ((a + b) & mask).asInstanceOf[Short]
  @inline private[this]  def minusOp(a: Short, b: Short): Short =
    ((a - b) & mask).asInstanceOf[Short]
  @inline private[this]  def incrOp(a: Short, b: Int): Short =
    ((a + b) & mask).asInstanceOf[Short]

  private[this] val mask = 255 + 255 * 256
}

object AtomicShort {
  def apply(initialValue: Short): AtomicShort =
    new AtomicShort(new AtomicInteger(initialValue))
}
