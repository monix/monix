package monifu.concurrent.locks

import monifu.concurrent.atomic.Atomic

class NaiveSpinLock extends Lock {
  private[this] val acquired = Atomic(false)

  @throws(classOf[InterruptedException])
  def acquire[T](cb: => T): T = {
    acquired.awaitCompareAndSet(expect = false, update = true)
    try cb finally acquired.set(update = false)
  }
}
