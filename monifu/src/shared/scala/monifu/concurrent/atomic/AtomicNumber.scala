package monifu.concurrent.atomic

/**
 * Represents an Atomic reference holding a number, providing helpers for easily incrementing and decrementing it.
 *
 * @tparam T should be something that's Numeric
 */
trait AtomicNumber[T] extends Atomic[T] {
  def increment(v: Int = 1): Unit
  def add(v: T): Unit
  def decrement(v: Int = 1): Unit
  def subtract(v: T): Unit

  def incrementAndGet(v: Int = 1): T
  def addAndGet(v: T): T
  def decrementAndGet(v: Int = 1): T
  def subtractAndGet(v: T): T

  def getAndIncrement(v: Int = 1): T
  def getAndAdd(v: T): T
  def getAndDecrement(v: Int = 1): T
  def getAndSubtract(v: T): T

  /**
   * Decrements this number until it reaches zero.
   *
   * @return a number representing how much it was able to subtract, which
   *         is a value between zero and `v`
   */
  def countDownToZero(v: T): T

  def `+=`(v: T): Unit
  def `-=`(v: T): Unit
}

object AtomicNumber {
  def apply[T, R <: AtomicNumber[T]](initialValue: T)(implicit ev: Numeric[T], builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}