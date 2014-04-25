package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference

final class AtomicAny[T] private[atomic] (underlying: AtomicReference[T]) extends Atomic[T] {
  def get: T = underlying.get()

  def set(update: T) {
    underlying.set(update)
  }

  def lazySet(update: T) {
    underlying.lazySet(update)
  }

  def compareAndSet(expect: T, update: T): Boolean =
    underlying.compareAndSet(expect, update)

  def weakCompareAndSet(expect: T, update: T): Boolean =
    underlying.weakCompareAndSet(expect, update)

  def getAndSet(update: T): T =
    underlying.getAndSet(update)
}

object AtomicAny {
  def apply[T](initialValue: T): AtomicAny[T] =
    new AtomicAny(new AtomicReference(initialValue))
}
