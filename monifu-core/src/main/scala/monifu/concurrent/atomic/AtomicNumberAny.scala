package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference

final class AtomicNumberAny[T : Numeric] private (underlying: AtomicReference[AnyRef])
  extends AtomicNumber[T] with BlockableAtomic[T] with WeakAtomic[T]
  with CommonOps[T] with NumberCommonOps[T] {

  private[this] val ev = implicitly[Numeric[T]]

  def plusOp(a: T, b: T): T =
    ev.plus(a, b)

  def minusOp(a: T, b: T): T =
    ev.minus(a, b)

  def incrOp(a: T, b: Int): T =
    ev.plus(a, ev.fromInt(b))

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
}

object AtomicNumberAny {
  def apply[T](initialValue: T)(implicit ev: Numeric[T]): AtomicNumberAny[T] =
    new AtomicNumberAny(new AtomicReference(initialValue.asInstanceOf[AnyRef]))
}
