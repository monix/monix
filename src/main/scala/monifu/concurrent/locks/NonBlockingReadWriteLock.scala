package monifu.concurrent.locks

import annotation.tailrec
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.ThreadLocal


/** 
 * Non-blocking version of a read-write lock, meant for low-contention scenarios.
 *
 *  - the read lock can be acquired by multiple threads at the same time
 *  - the write lock can be acquired by only a single thread and blocks all other
 *    reads and writes that are competing for the same lock
 *  - writes have priority, so a pending write will come before subsequent read attempts
 *
 * Not a good idea to use it in high-contention scenarios, as the locking is unfair
 * (i.e. writes have priority over reads, but otherwise it provides no guarantees
 * to the fairness of what thread gets the lock next)
 */
final class NonBlockingReadWriteLock private () extends ReadWriteLock {

  private[this] val activeReads = Atomic(0)
  private[this] val writePendingOrActive = Atomic(false)

  private[this] val localState = ThreadLocal(IDLE)
  private[this] val IDLE  = 0
  private[this] val READ  = 1
  private[this] val WRITE = 2

  /** 
   * Acquires a lock meant for reading. Multiple threads can
   * acquire the lock at the same time. It is also re-entrant (i.e. the same 
   * thread can acquire it multiple times)
   */
  def readLock[T](cb: => T): T = 
    localState.get match {
      /** 
       * Re-entrance check - if this thread already acquired either a READ,
       * or a WRITE lock, then reuse it.
       */
      case READ | WRITE =>
        cb
      case _ =>
        readLockAcquire()
        // signal that the currently acquired lock on this thread
        // is a read-lock (meant for re-entrance logic)
        localState.set(READ)

        try (cb) finally { 
          localState.set(IDLE)
          readLockRelease() 
        }
    }

  /** 
   * Acquires a lock meant for writing. Only one thread can
   * acquire the write lock at the same time and it has to wait until all
   * active reads are finished. 
   */
  def writeLock[T](cb: => T): T = 
    localState.get match {
      /** 
       * If the current thread already has the WRITE lock, no point in acquiring
       * it again.
       */
      case WRITE =>
        cb
      case _ =>
        // in case the currently acquired lock by the current thread is a READ
        // lock, then we need to release it and re-acquire it after we are finished.
        val fallbackToRead = 
          if (localState.get == READ) {
            readLockRelease()
            true
          }
          else 
            false

        writeLockAcquire()
        // signal that the currently acquired lock on this thread
        // is a write-lock (meant for re-entrance logic)
        localState.set(WRITE)

        try (cb) finally {        
          if (fallbackToRead) {
            // previous lock acquired by this thread was a READ lock
            // so we need to restore it
            localState.set(READ)
            writeLockRelease()
            readLockAcquire()
          }
          else {
            localState.set(IDLE)
            writeLockRelease()
          }
        }
    }

  /** 
   * Loops until is able to increment the `activeReads` count.
   * Waits for `writePendingOrActive` (the write lock) to become `false`.
   */
  @tailrec
  private[this] def readLockAcquire(): Unit = 
    if (writePendingOrActive.get)
      // keep retrying until no write is pending/active
      readLockAcquire()
    else {
      // optimistically assume that we can acquire the 
      // read lock by incrementing `activeReads`
      activeReads.increment
      // race-condition guard
      if (writePendingOrActive.get) {
        // no success, a write came in, so undo the activeReads
        // counter and keep retrying (writes have priority)
        activeReads.decrement
        readLockAcquire()
      }
    }

  private[this] def readLockRelease(): Unit = {
    /* Releasing the read-lock means decrementing just a counter.
     * When that counter reaches zero, then writes can proceed. */
    activeReads.decrement
  }  

  private[this] def writeLockAcquire(): Unit = {
    // Loops until is able to acquire the write lock.
    @tailrec
    def acquireWrite(): Unit = 
      if (!writePendingOrActive.compareAndSet(false, true))
        acquireWrite()

    /* Executed after the write lock is acquired,
     * loops until all active reads have finished.
     */
    @tailrec
    def waitForReadsToFinish(): Unit = {
      if (activeReads.get > 0)
        waitForReadsToFinish()
    } 

    acquireWrite()
    waitForReadsToFinish()
  }

  private[this] def writeLockRelease(): Unit = {
    writePendingOrActive.set(false)
  }
}


object NonBlockingReadWriteLock {
  def apply(): NonBlockingReadWriteLock =
    new NonBlockingReadWriteLock()
}