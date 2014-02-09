package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

final class AtomicNumberAny[T : Numeric] private (underlying: AtomicReference[T])
  extends AtomicAny(underlying) {

  private[this] val ev = implicitly[Numeric[T]]
  private[this] val one = ev.one

  def increment() = add(one)
  def increment(v: Int) = add(ev.fromInt(v))

  @tailrec
  def add(v: T) {
    val current = underlying.get()
    val update = ev.plus(current, v)
    if (!underlying.compareAndSet(current, update))
      add(v)
  }

  def decrement() = subtract(one)
  def decrement(v: Int) = subtract(ev.fromInt(v))

  @tailrec
  def subtract(v: T) {
    val current = underlying.get()
    val update = ev.minus(current, v)
    if (!underlying.compareAndSet(current, update))
      subtract(v)
  }

  def incrementAndGet(): T = addAndGet(one)
  def incrementAndGet(v: Int): T = addAndGet(ev.fromInt(v))

  @tailrec
  def addAndGet(v: T): T = {
    val current = underlying.get()
    val update = ev.plus(current, v)
    if (!underlying.compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  def decrementAndGet(): T = subtractAndGet(one)
  def decrementAndGet(v: Int): T = subtractAndGet(ev.fromInt(v))

  @tailrec
  def subtractAndGet(v: T): T = {
    val current = underlying.get()
    val update = ev.minus(current, v)
    if (!underlying.compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  def getAndIncrement(): T = getAndAdd(one)
  def getAndIncrement(v: Int): T = getAndAdd(ev.fromInt(v))

  @tailrec
  def getAndAdd(v: T): T = {
    val current = underlying.get()
    val update = ev.plus(current, v)
    if (!underlying.compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  def getAndDecrement(): T = getAndSubtract(one)
  def getAndDecrement(v: Int): T = getAndSubtract(ev.fromInt(v))

  @tailrec
  def getAndSubtract(v: T): T = {
    val current = underlying.get()
    val update = ev.minus(current, v)
    if (!underlying.compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }
}

object AtomicNumberAny {
  def apply[T](initialValue: T)(implicit ev: Numeric[T]): AtomicNumberAny[T] =
    new AtomicNumberAny(new AtomicReference(initialValue))

  def apply[T](ref: AtomicReference[T])(implicit ev: Numeric[T]): AtomicNumberAny[T] =
    new AtomicNumberAny(ref)
}
