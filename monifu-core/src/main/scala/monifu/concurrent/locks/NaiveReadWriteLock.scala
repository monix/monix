package monifu.concurrent.locks

import annotation.tailrec
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.ThreadLocal
import java.util.concurrent.TimeoutException


/**
 * Naive read-write lock, meant for low-contention scenarios.
 *
 *  - the read lock can be acquired by multiple threads at the same time
 *  - the write lock can be acquired by only a single thread and blocks all other
 *    reads and writes that are competing for the same lock
 *  - writes have priority, so a pending write will come before subsequent read attempts
 *  - it has re-entrant behavior. Upgrading from a read to a write lock involves 
 *    releasing the read-lock first, however downgrading from a write to a read lock
 *    is done directly
 *  - synchronization is based on spinlocks so threads never go into a wait-state (monitor-enter)
 *
 * Not a good idea to use it in high-contention scenarios, as the locking is unfair
 * (i.e. writes have priority over reads, but otherwise it provides no guarantees
 * to the fairness of what thread gets the lock next), plus spinlocking means that
 * threads never wait and thus consume CPU resources.
 *
 * Example:
 * {{{
 * class Fibonacci {
 *   private[this] val lock = new NaiveReadWriteLock()
 *   private[this] var a, b = 1
 *
 *   def get = lock.readLock(b)
 *
 *   def next() = lock.writeLock {
 *     val tmp = b
 *     b = a + b
 *     a = tmp
 *     get 
 *   }
 * }
 * }}}
 */
final class NaiveReadWriteLock private[locks] () {

  private[this] val IDLE  = 0
  private[this] val READ  = 1
  private[this] val WRITE = 2

  private[this] val localState = ThreadLocal(IDLE)
  private[this] val activeReads = Atomic(0)
  private[this] val writePendingOrActive = Atomic(false)

  /** 
   * Acquires a lock meant for reading. Multiple threads can
   * acquire the lock at the same time. It is also re-entrant (i.e. the same 
   * thread can acquire it multiple times)
   */
  @throws(classOf[InterruptedException])
  def readLock[T](cb: => T): T = 
    localState.get match {
      // Re-entrance check - if this thread already acquired either a READ,
      // or a WRITE lock, then reuse it.
      case READ | WRITE =>
        cb
      case IDLE =>
        readLockAcquire()
        try cb finally {
          readLockRelease()
        }
    }

  /** 
   * Acquires a lock meant for writing. Only one thread can
   * acquire the write lock at the same time and it has to wait until all
   * active reads are finished. 
   */
  @throws(classOf[InterruptedException])
  def writeLock[T](cb: => T): T = 
    localState.get match {
      // If the current thread already has the WRITE lock, no point in acquiring
      // it again.
      case WRITE =>
        cb
      case _ =>
        // in case the currently acquired lock by the current thread is a READ
        // lock, then we need to release it and re-acquire it after we are finished.
        val fallbackToRead = 
          if (localState.get != READ) false else {
            readLockRelease()
            true
          }

        writeLockAcquire()
        try cb finally {
          if (fallbackToRead) 
            downgradeWriteToReadLock()
          else 
            writeLockRelease()
        }
    }

  /** 
   * Loops until is able to increment the `activeReads` count.
   * Waits for `writePendingOrActive` (the write lock) to become `false`.
   */
  @tailrec
  @throws(classOf[InterruptedException])
  @throws(classOf[TimeoutException])
  private[this] def readLockAcquire(waitUntil: Long = 0): Unit = {
    // waits for writes to finish
    if (waitUntil != 0)
      writePendingOrActive.waitForValue(expect = false, waitUntil = waitUntil)
    else
      writePendingOrActive.waitForValue(expect = false)

    // optimistically assume that we can acquire the 
    // read lock by incrementing `activeReads`
    activeReads.increment()
    // race-condition guard
    if (writePendingOrActive.get) {
      // no success, a write came in, so undo the activeReads
      // counter and keep retrying (writes have priority)
      activeReads.decrement()
      readLockAcquire()
    }
    else {
      // signal that the currently acquired lock on this thread
      // is a read-lock (meant for re-entrance logic)
      localState.set(READ)
    }
  }

  @inline private[this] def readLockRelease(): Unit = {
    localState.set(IDLE)
    // when the `activeReads` counter reaches zero
    // then pending writes can proceed
    activeReads.decrement()
  }  

  /** 
   * Loops until is able to acquire a write lock
   */
  @inline private[this] def writeLockAcquire(): Unit = {
    // acquires the write lock
    writePendingOrActive.waitForCompareAndSet(expect=false, update=true)
    // waits until all active reads are finished
    activeReads.waitForValue(expect=0)
    localState.set(WRITE)
  }

  /** 
   * Downgrades a write lock to a read lock, so the current thread can
   * keep reading with a guarantee that the data that was written hasn't
   * changed in the transition.
   */
  @inline private[this] def downgradeWriteToReadLock() {
    localState.set(READ)
    activeReads.increment()
    writePendingOrActive.set(update = false)
  }

  @inline private[this] def writeLockRelease(): Unit = {
    localState.set(IDLE)
    writePendingOrActive.set(update = false)
  }
}

object NaiveReadWriteLock {
  def apply(): NaiveReadWriteLock =
    new NaiveReadWriteLock()
}