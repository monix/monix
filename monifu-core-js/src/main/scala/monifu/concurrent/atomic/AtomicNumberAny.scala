package monifu.concurrent.atomic

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

  def transformAndExtract[U](cb: (T) => (T, U)): U = {
    val (update, r) = cb(ref)
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

  def getAndIncrement(v: Int): T = {
    val c = ref
    ref = ev.plus(ref, ev.fromInt(v))
    c
  }

  def addAndGet(v: T): T = {
    incrementAndGet()
  }

  def incrementAndGet(v: Int): T = {
    ref = ev.plus(ref, ev.fromInt(v))
    ref
  }

  def add(v: T): Unit = {
    ref = ev.plus(ref, v)
  }

  def increment(v: Int): Unit = {
    ref = ev.plus(ref, ev.fromInt(v))
  }

  def increment(): Unit = increment(1)
  def decrement(v: Int): Unit = increment(-v)
  def decrement(): Unit = increment(-1)
  def incrementAndGet(): T = incrementAndGet(1)
  def decrementAndGet(v: Int): T = incrementAndGet(-v)
  def decrementAndGet(): T = incrementAndGet(-1)
  def getAndIncrement(): T = getAndIncrement(1)
  def getAndDecrement(): T = getAndIncrement(-1)
  def getAndDecrement(v: Int): T = getAndIncrement(-v)
  def `+=`(v: T): Unit = addAndGet(v)
  def `-=`(v: T): Unit = subtractAndGet(v)
}

object AtomicNumberAny {
  def apply[T : Numeric](initialValue: T): AtomicNumberAny[T] =
    new AtomicNumberAny[T](initialValue)
}
