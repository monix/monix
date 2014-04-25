package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

final class AtomicNumberAny[T : Numeric] private (underlying: AtomicReference[AnyRef]) extends AtomicNumber[T] {
  def get: T =
    underlying.get().asInstanceOf[T]

  def set(update: T): Unit =
    underlying.set(update.asInstanceOf[AnyRef])

  def lazySet(update: T): Unit =
    underlying.lazySet(update.asInstanceOf[AnyRef])

  def compareAndSet(expect: T, update: T): Boolean = {
    val current = underlying.get()
    current.asInstanceOf[T] == expect && underlying.compareAndSet(current, update.asInstanceOf[AnyRef])
  }

  def weakCompareAndSet(expect: T, update: T): Boolean = {
    val current = underlying.get()
    current.asInstanceOf[T] == expect && underlying.weakCompareAndSet(current, update.asInstanceOf[AnyRef])
  }

  def getAndSet(update: T): T =
    underlying.getAndSet(update.asInstanceOf[AnyRef]).asInstanceOf[T]

  @tailrec
  def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: T): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int): T = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: T): T = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int): T = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: T): T = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: T): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: T): T = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: T): T = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  private[this] val ev = implicitly[Numeric[T]]
  @inline private[this] def plusOp(a: T, b: T): T = ev.plus(a, b)
  @inline private[this] def minusOp(a: T, b: T): T = ev.minus(a, b)
  @inline private[this] def incrOp(a: T, b: Int): T = ev.plus(a, ev.fromInt(b))
}

object AtomicNumberAny {
  def apply[T](initialValue: T)(implicit ev: Numeric[T]): AtomicNumberAny[T] =
    new AtomicNumberAny(new AtomicReference(initialValue.asInstanceOf[AnyRef]))
}
