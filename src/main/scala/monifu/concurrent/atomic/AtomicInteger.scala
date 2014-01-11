package monifu.concurrent.atomic

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec


final class AtomicInt private (ref: AtomicInteger) extends AtomicNumber[Int] {
  type Underlying = AtomicInteger
  def asJava = ref

  def get: Int = ref.get()
  override def apply(): Int = ref.get()
  def set(update: Int) = ref.set(update)
  def lazySet(update: Int) = ref.lazySet(update)

  def compareAndSet(expect: Int, update: Int): Boolean =
    ref.compareAndSet(expect, update)

  def weakCompareAndSet(expect: Int, update: Int): Boolean =
    ref.weakCompareAndSet(expect, update)

  def naturalCompareAndSet(expect: Int, update: Int): Boolean =
    ref.compareAndSet(expect, update)

  def naturalWeakCompareAndSet(expect: Int, update: Int): Boolean =
    ref.weakCompareAndSet(expect, update)

  def getAndSet(update: Int): Int =
    ref.getAndSet(update)

  @tailrec
  def transformAndExtract[U](cb: (Int) => (Int, U)): U = {
    val current = ref.get()
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def weakTransformAndExtract[U](cb: (Int) => (Int, U)): U = {
    val current = ref.get()
    val (update, extract) = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Int) => Int): Int = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def weakTransformAndGet(cb: (Int) => Int): Int = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Int) => Int): Int = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def weakGetAndTransform(cb: (Int) => Int): Int = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Int) => Int) {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def weakTransform(cb: (Int) => Int) {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransform(cb)
  }

  def increment() {
    ref.incrementAndGet()
  }

  def increment(v: Int) {
    ref.addAndGet(v)
  }

  def add(v: Int) {
    ref.addAndGet(v)
  }

  def decrement() {
    ref.decrementAndGet()
  }

  def decrement(v: Int) {
    ref.addAndGet(-v)
  }

  def subtract(v: Int) {
    ref.addAndGet(-v)
  }

  def incrementAndGet(): Int = ref.incrementAndGet()
  def incrementAndGet(v: Int): Int = ref.addAndGet(v)
  def addAndGet(v: Int): Int = ref.addAndGet(v)

  def decrementAndGet(): Int = ref.decrementAndGet()
  def decrementAndGet(v: Int): Int = ref.addAndGet(-v)
  def subtractAndGet(v: Int): Int = ref.addAndGet(-v)

  def getAndIncrement(): Int = ref.getAndIncrement
  def getAndIncrement(v: Int): Int = ref.getAndAdd(v)
  def getAndAdd(v: Int): Int = ref.getAndAdd(v)

  def getAndDecrement(): Int = ref.getAndDecrement
  def getAndDecrement(v: Int): Int = ref.getAndAdd(-v)
  def getAndSubtract(v: Int): Int = ref.getAndAdd(-v)
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt = new AtomicInt(new AtomicInteger(initialValue))
  def apply(ref: AtomicInteger): AtomicInt = new AtomicInt(ref)
}
