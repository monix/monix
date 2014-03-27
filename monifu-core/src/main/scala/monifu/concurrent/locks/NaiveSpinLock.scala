package monifu.concurrent.locks

import monifu.concurrent.atomic.Atomic

final class NaiveSpinLock private[locks] () extends Lock {
  private[this] val acquired = Atomic(false)

  @throws(classOf[InterruptedException])
  def acquire[T](cb: => T): T = {
    acquired.awaitCompareAndSet(expect = false, update = true)
    try cb finally acquired.set(update = false)
  }
}

object NaiveSpinLock {
  def apply(): NaiveSpinLock =
    new NaiveSpinLock()
}
