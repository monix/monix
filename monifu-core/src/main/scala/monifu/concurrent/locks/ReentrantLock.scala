package monifu.concurrent.locks

import monifu.concurrent.atomic.Atomic

final class ReentrantLock private[locks] () extends Lock {
  private[this] val acquired = Atomic(false)
  private[this] val isReentrant = new ThreadLocal[Boolean]() {
    override def initialValue(): Boolean = false
  }

  @throws(classOf[InterruptedException])
  def acquire[T](cb: => T): T = {
    // acquire the lock only if the current thread hasn't already acquired it
    val acquireAndRelease = !isReentrant.get

    if (acquireAndRelease) {
      // try acquiring it through spin-locking, give up after 100 tries
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
        // setting the lock to false involves
        acquired.set(update = false)
      }
    }
  }
}

object ReentrantLock {
  def apply(): ReentrantLock =
    new ReentrantLock()
}
