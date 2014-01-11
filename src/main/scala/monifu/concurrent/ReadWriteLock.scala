package monifu.concurrent

import annotation.tailrec
import monifu.concurrent.atomic.Atomic
import concurrent.duration._

final class ReadWriteLock private () {
  def readLock[T](cb: => T): T = {
    readLock()
    try(cb) finally { readLockRelease() }
  }

  def writeLock[T](cb: => T): T = {
    writeLock()
    try(cb) finally { writeLockRelease() }
  }

  private[this] val IDLE = 0
  private[this] val READ = 1
  private[this] val WRITE = 2

  private[this] val activeReads = Atomic(0)
  private[this] val writeActive = Atomic(false)
  private[this] val localGuard = ThreadLocal(IDLE)

  private[concurrent] def readLock(): Unit = {
    /** 
     * Loops until is able to increment the `activeReads` count.
     * Waits for `writeActive` (the write lock) to become `false`.
     */
    @tailrec
    def tryAcquisition(): Unit = 
      if (writeActive.get)
        // keep retrying until no write is pending/active
        tryAcquisition()
      else {
        // optimistically assume that we can acquire the 
        // read lock and do it
        activeReads.incrementAndGet
        // race-condition guard
        if (writeActive.get) {
          // no success, a write came in, so undo the activeReads
          // counter and keep retrying
          activeReads.decrementAndGet
          tryAcquisition()
        }
      }

    // ensure that another lock wasn't acquired by the same
    // thread (this lock is NOT reentrant)
    acquireLocalGuard(READ)
    tryAcquisition()
  }

  private[concurrent] def readLockRelease(): Unit = {
    releaseLocalGuard(READ)
    activeReads.decrementAndGet
  }  


  private[concurrent] def writeLock(): Unit = {
    /** 
     * Loops until is able to acquire the write lock.
     */
    @tailrec
    def acquireWrite(): Unit = 
      if (!writeActive.compareAndSet(false, true))
        acquireWrite()

    /** 
     * Executed after the write lock is acquired,
     * loops until all active reads have finished.
     */
    @tailrec
    def waitForReadsToFinish(): Unit = {
      if (activeReads.get > 0)
        waitForReadsToFinish()
    } 

    acquireLocalGuard(WRITE)
    acquireWrite()
    waitForReadsToFinish()
  }

  private[concurrent] def writeLockRelease(): Unit = {
    releaseLocalGuard(WRITE)
    // this write doesn't only release the lock, but also
    // ensures writes that happened in this thread are
    // published because writes into AtomicReferences also
    // create memory barriers (see JSR-133 for details)
    writeActive.set(false)
  }

  @inline
  private[this] def acquireLocalGuard(state: Int): Unit = {
    if (localGuard.get != IDLE)
      throw new IllegalStateException("ReadWriteLock is not reentrant (a thread can only acquire a single lock)")
    localGuard.set(state)
  }

  @inline
  private[this] def releaseLocalGuard(state: Int): Unit = {
    if (localGuard.get != state) {
      val label = if (state == READ) "ReadWriteLock.readLock" else "ReadWriteLock.writeLock"
      throw new IllegalStateException(s"$label was not acquired, so cannot release it")
    }
    localGuard.set(IDLE)
  }
}

object ReadWriteLock {
  def apply(): ReadWriteLock =
    new ReadWriteLock()
}