package monifu.concurrent.atomic

import java.util.concurrent.atomic.{AtomicBoolean => JavaAtomicBoolean}
import scala.annotation.tailrec

class AtomicBoolean protected (ref: JavaAtomicBoolean) extends Atomic[Boolean] {
  final type Underlying = JavaAtomicBoolean
  final def asJava = ref

  final def get: Boolean = ref.get()
  final def set(update: Boolean) = ref.set(update)
  final def lazySet(update: Boolean) = ref.lazySet(update)

  final def compareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.compareAndSet(expect, update)
  def weakCompareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.weakCompareAndSet(expect, update)
  def naturalCompareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.compareAndSet(expect, update)
  def naturalWeakCompareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.weakCompareAndSet(expect, update)
  def getAndSet(update: Boolean): Boolean =
    ref.getAndSet(update)

  @tailrec
  final def transformAndExtract[U](cb: (Boolean) => (Boolean, U)): U = {
    val current = ref.get()
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def weakTransformAndExtract[U](cb: (Boolean) => (Boolean, U)): U = {
    val current = ref.get()
    val (update, extract) = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (Boolean) => Boolean): Boolean = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def weakTransformAndGet(cb: (Boolean) => Boolean): Boolean = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (Boolean) => Boolean): Boolean = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def weakGetAndTransform(cb: (Boolean) => Boolean): Boolean = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (Boolean) => Boolean) {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  final def weakTransform(cb: (Boolean) => Boolean) {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransform(cb)
  }
}

object AtomicBoolean {
  def apply(initialValue: Boolean): AtomicBoolean = new AtomicBoolean(new JavaAtomicBoolean(initialValue))
  def apply(ref: JavaAtomicBoolean): AtomicBoolean = new AtomicBoolean(ref)
}
