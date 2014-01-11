package monifu.concurrent.atomic

import java.util.concurrent.atomic.{AtomicLong => JavaAtomicLong}
import scala.annotation.tailrec

final class AtomicLong private (ref: JavaAtomicLong) extends AtomicNumber[Long] {
  type Underlying = JavaAtomicLong
  def asJava = ref

  def get: Long = ref.get()
  def set(update: Long) = ref.set(update)
  def lazySet(update: Long) = ref.lazySet(update)

  def compareAndSet(expect: Long, update: Long): Boolean =
    ref.compareAndSet(expect, update)
  def weakCompareAndSet(expect: Long, update: Long): Boolean =
    ref.weakCompareAndSet(expect, update)
  def naturalCompareAndSet(expect: Long, update: Long): Boolean =
    ref.compareAndSet(expect, update)
  def naturalWeakCompareAndSet(expect: Long, update: Long): Boolean =
    ref.weakCompareAndSet(expect, update)
  def getAndSet(update: Long): Long =
    ref.getAndSet(update)

  @tailrec
  def transformAndExtract[U](cb: (Long) => (Long, U)): U = {
    val current = ref.get()
    val (update, extract) = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def weakTransformAndExtract[U](cb: (Long) => (Long, U)): U = {
    val current = ref.get()
    val (update, extract) = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Long) => Long): Long = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def weakTransformAndGet(cb: (Long) => Long): Long = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Long) => Long): Long = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def weakGetAndTransform(cb: (Long) => Long): Long = {
    val current = ref.get()
    val update = cb(current)
    if (!ref.weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Long) => Long) {
    val current = ref.get()
    val update = cb(current)
    if (!ref.compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def weakTransform(cb: (Long) => Long) {
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

  def add(v: Long) {
    ref.addAndGet(v)
  }

  def decrement() {
    ref.decrementAndGet()
  }

  def decrement(v: Int) {
    ref.addAndGet(-v)
  }

  def subtract(v: Long) {
    ref.addAndGet(-v)
  }

  def incrementAndGet(): Long = ref.incrementAndGet()
  def incrementAndGet(v: Int): Long = ref.addAndGet(v)
  def addAndGet(v: Long): Long = ref.addAndGet(v)

  def decrementAndGet(): Long = ref.decrementAndGet()
  def decrementAndGet(v: Int): Long = ref.addAndGet(-v)
  def subtractAndGet(v: Long): Long = ref.addAndGet(-v)

  def getAndIncrement(): Long = ref.getAndIncrement
  def getAndIncrement(v: Int): Long = ref.getAndAdd(v)
  def getAndAdd(v: Long): Long = ref.getAndAdd(v)

  def getAndDecrement(): Long = ref.getAndDecrement
  def getAndDecrement(v: Int): Long = ref.getAndAdd(-v)
  def getAndSubtract(v: Long): Long = ref.getAndAdd(-v)
}

object AtomicLong {
  def apply(initialValue: Long): AtomicLong = new AtomicLong(new JavaAtomicLong(initialValue))
  def apply(ref: JavaAtomicLong): AtomicLong = new AtomicLong(ref)
}