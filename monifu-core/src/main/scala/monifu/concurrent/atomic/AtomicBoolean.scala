package monifu.concurrent.atomic

import java.util.concurrent.atomic.{AtomicBoolean => JavaAtomicBoolean}

final class AtomicBoolean private (ref: JavaAtomicBoolean) extends Atomic[Boolean] {
  def get: Boolean = ref.get()
  
  def set(update: Boolean) = ref.set(update)

  def lazySet(update: Boolean) = ref.lazySet(update)

  def compareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Boolean): Boolean =
    ref.getAndSet(update)
}

object AtomicBoolean {
  def apply(initialValue: Boolean): AtomicBoolean =
    new AtomicBoolean(new JavaAtomicBoolean(initialValue))
}
