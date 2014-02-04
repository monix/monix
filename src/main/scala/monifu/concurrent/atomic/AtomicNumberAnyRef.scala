package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

final class AtomicNumberAnyRef[T <: AnyRef : Numeric] private (underlying: AtomicReference[T]) 
  extends AtomicAnyRef(underlying) {

  private[this] val ev = implicitly[Numeric[T]]
  private[this] val one = ev.one

  final def increment() = add(one)
  final def increment(v: Int) = add(ev.fromInt(v))

  @tailrec
  final def add(v: T) {
    val current = underlying.get()
    val update = ev.plus(current, v)
    if (!underlying.compareAndSet(current, update))
      add(v)
  }

  final def decrement() = subtract(one)
  final def decrement(v: Int) = subtract(ev.fromInt(v))

  @tailrec
  final def subtract(v: T) {
    val current = underlying.get()
    val update = ev.minus(current, v)
    if (!underlying.compareAndSet(current, update))
      subtract(v)
  }

  final def incrementAndGet(): T = addAndGet(one)
  final def incrementAndGet(v: Int): T = addAndGet(ev.fromInt(v))

  @tailrec
  final def addAndGet(v: T): T = {
    val current = underlying.get()
    val update = ev.plus(current, v)
    if (!underlying.compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  final def decrementAndGet(): T = subtractAndGet(one)
  final def decrementAndGet(v: Int): T = subtractAndGet(ev.fromInt(v))

  @tailrec
  final def subtractAndGet(v: T): T = {
    val current = underlying.get()
    val update = ev.minus(current, v)
    if (!underlying.compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  final def getAndIncrement(): T = getAndAdd(one)
  final def getAndIncrement(v: Int): T = getAndAdd(ev.fromInt(v))

  @tailrec
  final def getAndAdd(v: T): T = {
    val current = underlying.get()
    val update = ev.plus(current, v)
    if (!underlying.compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  final def getAndDecrement(): T = getAndSubtract(one)
  final def getAndDecrement(v: Int): T = getAndSubtract(ev.fromInt(v))

  @tailrec
  final def getAndSubtract(v: T): T = {
    val current = underlying.get()
    val update = ev.minus(current, v)
    if (!underlying.compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }
}

object AtomicNumberAnyRef {
  def apply[T <: AnyRef](initialValue: T)(implicit ev: Numeric[T]): AtomicNumberAnyRef[T] =
    new AtomicNumberAnyRef(new AtomicReference(initialValue))

  def apply[T <: AnyRef](ref: AtomicReference[T])(implicit ev: Numeric[T]): AtomicNumberAnyRef[T] =
    new AtomicNumberAnyRef(ref)
}
