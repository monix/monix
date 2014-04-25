package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

final class AtomicInt private (ref: AtomicInteger) extends AtomicNumber[Int] {
  def get: Int = ref.get()
  def set(update: Int) = ref.set(update)
  def lazySet(update: Int) = ref.lazySet(update)

  def compareAndSet(expect: Int, update: Int): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Int, update: Int): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Int): Int =
    ref.getAndSet(update)

  override def increment(): Unit =
    ref.incrementAndGet()

  override def decrement(): Unit =
    ref.decrementAndGet()

  override def incrementAndGet(): Int =
    ref.incrementAndGet()

  override def decrementAndGet(): Int =
    ref.decrementAndGet()

  override def decrementAndGet(v: Int): Int =
    ref.addAndGet(-v)

  override def getAndIncrement(): Int =
    ref.getAndIncrement

  override def getAndDecrement(): Int =
    ref.getAndDecrement

  override def getAndDecrement(v: Int): Int =
    ref.getAndAdd(-v)

  override def decrement(v: Int): Unit =
    ref.addAndGet(-v)

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Int): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): Int = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Int): Int = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): Int = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Int): Int = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Int): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Int): Int = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Int): Int = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @inline private[this] def plusOp(a: Int, b: Int) = a + b
  @inline private[this] def minusOp(a: Int, b: Int) = a - b
  @inline private[this] def incrOp(a: Int, b: Int): Int = a + b
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt = new AtomicInt(new AtomicInteger(initialValue))
}
