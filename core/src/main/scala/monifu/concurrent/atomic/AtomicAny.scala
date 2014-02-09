package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference

class AtomicAny[T] private[atomic] (underlying: AtomicReference[T]) extends Atomic[T] {
  final override type Underlying = AtomicReference[T]
  final override def asJava = underlying

  final override def get: T = underlying.get()
  final override def apply(): T = underlying.get()

  final def set(update: T) {
    underlying.set(update)
  }

  final def lazySet(update: T) {
    underlying.lazySet(update)
  }

  final def compareAndSet(expect: T, update: T): Boolean =
    underlying.compareAndSet(expect, update)

  final def weakCompareAndSet(expect: T, update: T): Boolean =
    underlying.weakCompareAndSet(expect, update)

  final def getAndSet(update: T): T =
    underlying.getAndSet(update)
}

object AtomicAny {
  def apply[T](initialValue: T): AtomicAny[T] =
    new AtomicAny(new AtomicReference(initialValue))

  def apply[T](ref: AtomicReference[T]): AtomicAny[T] =
    new AtomicAny(ref)
}
