package monifu.concurrent.atomic

/**
 * Represents an Atomic reference holding a number, providing helpers for easily incrementing and decrementing it.
 *
 * @tparam T should be something that's Numeric
 */
trait AtomicNumber[@specialized T] extends Atomic[T] {
  def increment(v: Int): Unit
  def add(v: T): Unit

  def incrementAndGet(v: Int): T
  def addAndGet(v: T): T

  def getAndIncrement(v: Int): T
  def getAndAdd(v: T): T

  def subtract(v: T): Unit

  def subtractAndGet(v: T): T
  def getAndSubtract(v: T): T

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

object AtomicNumber {
  def apply[T, R <: AtomicNumber[T]](initialValue: T)(implicit ev: Numeric[T], builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}