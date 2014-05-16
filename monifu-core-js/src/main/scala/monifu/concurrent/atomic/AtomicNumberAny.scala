package monifu.concurrent.atomic

import scala.annotation.tailrec

final class AtomicNumberAny[T : Numeric] private[atomic] (initialValue: T) extends AtomicNumber[T] {
  private[this] val ev = implicitly[Numeric[T]]
  private[this] var ref = initialValue

  def getAndSet(update: T): T = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: T, update: T): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: T): Unit = {
    ref = update
  }

  def get: T = ref

  @inline
  def update(value: T): Unit = set(value)

  @inline
  def `:=`(value: T): Unit = set(value)

  @inline
  def lazySet(update: T): Unit = set(update)

  def transformAndExtract[U](cb: (T) => (U, T)): U = {
    val (r, update) = cb(ref)
    ref = update
    r
  }

  def transformAndGet(cb: (T) => T): T = {
    val update = cb(ref)
    ref = update
    update
  }

  def getAndTransform(cb: (T) => T): T = {
    val current = ref
    ref = cb(ref)
    current
  }

  def transform(cb: (T) => T): Unit = {
    ref = cb(ref)
  }

  def getAndSubtract(v: T): T = {
    val c = ref
    ref = ev.minus(ref, v)
    c
  }

  def subtractAndGet(v: T): T = {
    ref = ev.minus(ref, v)
    ref
  }

  def subtract(v: T): Unit = {
    ref = ev.minus(ref, v)
  }

  def getAndAdd(v: T): T = {
    val c = ref
    ref = ev.plus(ref, v)
    c
  }

  def getAndIncrement(v: Int = 1): T = {
    val c = ref
    ref = ev.plus(ref, ev.fromInt(v))
    c
  }

  def addAndGet(v: T): T = {
    ref = ev.plus(ref, v)
    ref
  }

  def incrementAndGet(v: Int = 1): T = {
    ref = ev.plus(ref, ev.fromInt(v))
    ref
  }

  def add(v: T): Unit = {
    ref = ev.plus(ref, v)
  }

  def increment(v: Int = 1): Unit = {
    ref = ev.plus(ref, ev.fromInt(v))
  }

  def countDownToZero(v: T = ev.one): T = {
    val current = get
    if (current != ev.zero) {
      val decrement = if (ev.compare(current, v) >= 0) v else current
      ref = ev.minus(current, decrement)
      decrement
    }
    else
      ev.zero
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): T = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): T = getAndIncrement(-v)
  def `+=`(v: T): Unit = addAndGet(v)
  def `-=`(v: T): Unit = subtractAndGet(v)
}

object AtomicNumberAny {
  def apply[T : Numeric](initialValue: T): AtomicNumberAny[T] =
    new AtomicNumberAny[T](initialValue)
}
