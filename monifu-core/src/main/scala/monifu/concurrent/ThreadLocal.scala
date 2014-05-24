package monifu.concurrent

object ThreadLocal {
  def apply[T](initial: T) = new ThreadLocal[T] {
    override val initialValue = initial
  }
}
