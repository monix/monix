package monifu.concurrent.atomic

trait Atomic[@specialized T] {
  type Underlying
  def asJava: Underlying

  def get: T
  def apply(): T = get

  def set(update: T): Unit
  def update(value: T): Unit = set(value)
  def `:=`(value: T): Unit = set(value)

  def lazySet(update: T)

  def compareAndSet(expect: T, update: T): Boolean
  def weakCompareAndSet(expect: T, update: T): Boolean
  def getAndSet(update: T): T

  def transformAndGet(cb: T => T): T
  def transformAndExtract[U](cb: T => (T, U)): U
  def weakTransformAndExtract[U](cb: T => (T, U)): U
  
  def weakTransformAndGet(cb: T => T): T

  def getAndTransform(cb: T => T): T
  def weakGetAndTransform(cb: T => T): T

  def transform(cb: T => T)
  def weakTransform(cb: T => T)
}

object Atomic {
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}



