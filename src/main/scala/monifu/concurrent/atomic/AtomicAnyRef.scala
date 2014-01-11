package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

class AtomicAnyRef[T <: AnyRef] private[atomic] (underlying: AtomicReference[T]) extends Atomic[T] {
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

  final def naturalCompareAndSet(expect: T, update: T): Boolean = {
    val current = underlying.get()
    expect == current && underlying.compareAndSet(current, update)
  }

  final def naturalWeakCompareAndSet(expect: T, update: T): Boolean = {
    val current = underlying.get()
    expect == current && underlying.weakCompareAndSet(current, update)
  }

  final def getAndSet(update: T): T =
    underlying.getAndSet(update)

  @tailrec
  final def transformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = underlying.get()
    val (update, extract) = cb(current)
    if (!underlying.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def weakTransformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = underlying.get()
    val (update, extract) = cb(current)
    if (!underlying.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (T) => T): T = {
    val current = underlying.get()
    val update = cb(current)
    if (!underlying.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def weakTransformAndGet(cb: (T) => T): T = {
    val current = underlying.get()
    val update = cb(current)
    if (!underlying.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (T) => T): T = {
    val current = underlying.get()
    val update = cb(current)
    if (!underlying.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def weakGetAndTransform(cb: (T) => T): T = {
    val current = underlying.get()
    val update = cb(current)
    if (!underlying.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (T) => T) {
    val current = underlying.get()
    val update = cb(current)
    if (!underlying.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  final def weakTransform(cb: (T) => T) {
    val current = underlying.get()
    val update = cb(current)
    if (!underlying.compareAndSet(current, update))
      weakTransform(cb)
  }
}

object AtomicAnyRef {
  def apply[T <: AnyRef](initialValue: T): AtomicAnyRef[T] =
    new AtomicAnyRef(new AtomicReference(initialValue))

  def apply[T <: AnyRef](ref: AtomicReference[T]): AtomicAnyRef[T] =
    new AtomicAnyRef(ref)
}
