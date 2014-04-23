package monifu.concurrent.locks

import monifu.concurrent.atomic.Atomic

final class NaiveSpinLock private[locks] () extends Lock {
  private[this] val acquired = Atomic(false)
  private[this] val isReentrant = new ThreadLocal[Boolean]() {
    override def initialValue(): Boolean = false
  }

  @throws(classOf[InterruptedException])
  def acquire[T](cb: => T): T = {
    // acquire the lock only if the current thread hasn't already acquired it
    val acquireAndRelease = !isReentrant.get

    if (acquireAndRelease) {
      // spin-lock, until it succeeds
      acquired.waitForCompareAndSet(expect = false, update = true)
      isReentrant.set(true)
    }

    try {
      cb
    }
    finally {
      // if this acquisition wasn't reentrant, then needs to release the lock
      if (acquireAndRelease) {
        isReentrant.set(false)
        // setting the lock to false also involves a volatile store
        // which insures a happens-before relationship for the operations
        // that happened on this thread before the release
        acquired.set(update = false)
      }
    }
  }
}

object NaiveSpinLock {
  def apply(): NaiveSpinLock =
    new NaiveSpinLock()
}
