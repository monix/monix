package monifu.concurrent.locks

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.ThreadLocal

final class ReentrantLock private[locks] () extends Lock {
  private[this] val acquired = Atomic(false)
  private[this] val reentrancyCounter = ThreadLocal(0L)
  private[this] val pulse = new AnyRef

  @throws(classOf[InterruptedException])
  def acquire[T](cb: => T): T = {
    // acquire the lock only if the current thread hasn't already acquired it
    if (reentrancyCounter.get == 0)
      // try acquiring it through spin-locking, give up after 100 tries
      while (!acquired.waitForCompareAndSet(expect = false, update = true, maxRetries = 100))
        pulse.synchronized {
          // guarding against race condition (i.e. retrying one last time)
          if (!acquired.compareAndSet(expect = false, update = true))
            // putting the thread to sleep
            pulse.wait()
        }

    // used for reentrancy logic - if bigger than zero, then the current thread
    // already acquired this lock one or more times
    reentrancyCounter := reentrancyCounter.get + 1
    
    try cb finally {
      reentrancyCounter := reentrancyCounter.get - 1
      assert(reentrancyCounter.get >= 0, s"Invalid reentrancy counter ${reentrancyCounter.get} < 0")

      if (reentrancyCounter.get == 0)
        pulse.synchronized {
          acquired.set(update = false)
          pulse.notify()
        }
    }
  }
}

object ReentrantLock {
  def apply(): ReentrantLock =
    new ReentrantLock()
}
