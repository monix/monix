package monifu.concurrent.atomic

trait Atomic[T] {
  def get: T
  def apply(): T = get

  def set(update: T): Unit
  def update(value: T): Unit = set(value)
  def `:=`(value: T): Unit = set(value)

  def compareAndSet(expect: T, update: T): Boolean
  def getAndSet(update: T): T

  def transformAndExtract[U](cb: (T) => (T, U)): U
  def transformAndGet(cb: (T) => T): T
  def getAndTransform(cb: (T) => T): T
  def transform(cb: (T) => T): Unit
}

object Atomic {
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}