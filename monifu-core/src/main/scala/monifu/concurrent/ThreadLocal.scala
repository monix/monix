package monifu.concurrent

final class ThreadLocal[@specialized T] private (initial: T) {
  private[this] val ref = 
    new java.lang.ThreadLocal[T] {
      override def initialValue = initial
    }

  def get: T = ref.get
  def apply(): T = ref.get
  def update(value: T): Unit = ref.set(value)
  def `:=`(update: T): Unit = ref.set(update)
  def set(update: T): Unit = ref.set(update)
}

object ThreadLocal {
  def apply[T](initialValue: T): ThreadLocal[T] =
    new ThreadLocal(initialValue)
}