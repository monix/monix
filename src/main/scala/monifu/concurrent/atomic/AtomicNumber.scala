package monifu.concurrent.atomic

trait AtomicNumber[@specialized T] extends Atomic[T] {
  def increment(): Unit
  def increment(v: Int): Unit
  def add(v: T): Unit

  def decrement(): Unit
  def decrement(v: Int): Unit
  def subtract(v: T): Unit

  def incrementAndGet(): T
  def incrementAndGet(v: Int): T
  def addAndGet(v: T): T

  def decrementAndGet(): T
  def decrementAndGet(v: Int): T
  def subtractAndGet(v: T): T

  def getAndIncrement(): T
  def getAndIncrement(v: Int): T
  def getAndAdd(v: T): T

  def getAndDecrement(): T
  def getAndDecrement(v: Int): T
  def getAndSubtract(v: T): T

  def `+=`(v: T): T = addAndGet(v)
  def `-=`(v: T): T = subtractAndGet(v)
}

object AtomicNumber {
  def apply[T, R <: AtomicNumber[T]](initialValue: T)(implicit ev: Numeric[T], builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}