package monifu.concurrent.atomic

/**
 * Represents an Atomic reference holding a number, providing helpers for easily incrementing and decrementing it.
 *
 * @tparam T should be something that's Numeric
 */
trait AtomicNumber[T] extends Atomic[T] {
  def increment(v: Int): Unit
  def add(v: T): Unit

  def incrementAndGet(v: Int): T
  def addAndGet(v: T): T

  def getAndIncrement(v: Int): T
  def getAndAdd(v: T): T

  def subtract(v: T): Unit

  def subtractAndGet(v: T): T
  def getAndSubtract(v: T): T

  def increment(): Unit
  def decrement(v: Int): Unit
  def decrement(): Unit
  def incrementAndGet(): T
  def decrementAndGet(v: Int): T
  def decrementAndGet(): T
  def getAndIncrement(): T
  def getAndDecrement(): T
  def getAndDecrement(v: Int): T

  def `+=`(v: T): Unit
  def `-=`(v: T): Unit
}

object AtomicNumber {
  def apply[T, R <: AtomicNumber[T]](initialValue: T)(implicit ev: Numeric[T], builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}