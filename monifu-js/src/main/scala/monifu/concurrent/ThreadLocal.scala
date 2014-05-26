package monifu.concurrent

/**
 * Represents a ThreadLocal, a concept that isn't useful on top of a Javascript runtime
 * (since in a JS runtime all variables are thread-local, since it's a single threaded
 * execution model), but having this is useful for cross-compilation purposes.
 */
final class ThreadLocal[T] private (initial: T) {
  private[this] var ref = initial

  def get: T = ref
  def apply(): T = ref
  def update(value: T): Unit = ref = value
  def `:=`(update: T): Unit = ref = update
  def set(update: T): Unit = ref = update
}

object ThreadLocal {
  def apply[T](initialValue: T): ThreadLocal[T] =
    new ThreadLocal(initialValue)
}