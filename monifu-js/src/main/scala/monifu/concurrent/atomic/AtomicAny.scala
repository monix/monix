package monifu.concurrent.atomic

final class AtomicAny[T] private[atomic] (initialValue: T) extends Atomic[T] {
  private[this] var ref = initialValue

  def getAndSet(update: T): T = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: T, update: T): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: T): Unit = {
    ref = update
  }

  def get: T = ref

  def transformAndExtract[U](cb: (T) => (U, T)): U = {
    val (r, update) = cb(ref)
    ref = update
    r
  }

  def transformAndGet(cb: (T) => T): T = {
    val update = cb(ref)
    ref = update
    update
  }

  def getAndTransform(cb: (T) => T): T = {
    val current = ref
    ref = cb(ref)
    current
  }

  def transform(cb: (T) => T): Unit = {
    ref = cb(ref)
  }

  @inline
  def update(value: T): Unit = set(value)

  @inline
  def `:=`(value: T): Unit = set(value)

  @inline
  def lazySet(update: T): Unit = set(update)
}

object AtomicAny {
  def apply[T](initialValue: T): AtomicAny[T] =
    new AtomicAny(initialValue)
}
