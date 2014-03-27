package monifu.concurrent.atomic

import scala.annotation.tailrec

/**
 * Private trait having reusable and specialised implementations for the methods
 * specified by `Atomic[T]` - it's raison d'être being that `Atomic[T]` can't be specialized
 * directly, as it is also used by the Scala.js implementation.
 */
private[atomic] trait CommonOps[@specialized T] { self: Atomic[T] =>
  @tailrec
  final def transformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = get
    val (update, extract) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }
}

