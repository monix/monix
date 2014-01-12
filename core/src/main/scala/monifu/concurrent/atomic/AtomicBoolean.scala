package monifu.concurrent.atomic

import java.util.concurrent.atomic.{AtomicBoolean => JavaAtomicBoolean}
import scala.annotation.tailrec

final class AtomicBoolean private (ref: JavaAtomicBoolean) extends Atomic[Boolean] {
  type Underlying = JavaAtomicBoolean
  def asJava = ref

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
  def apply(initialValue: Boolean): AtomicBoolean = new AtomicBoolean(new JavaAtomicBoolean(initialValue))
  def apply(ref: JavaAtomicBoolean): AtomicBoolean = new AtomicBoolean(ref)
}
