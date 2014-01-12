package monifu.concurrent.locks

import monifu.concurrent.ThreadLocal

final class ReentrantReadWriteLock private () extends ReadWriteLock {
  private[this] val lock = new java.util.concurrent.locks.ReentrantReadWriteLock()
  private[this] val localState = ThreadLocal(IDLE)

  private[this] val IDLE  = 0
  private[this] val READ  = 1
  private[this] val WRITE = 2

  def readLock[T](cb: => T): T = 
    localState.get match {
      case READ | WRITE =>
        cb
      case _ =>
        lock.readLock.lock()
        localState.set(READ)

        try (cb) finally { 
          localState.set(IDLE)
          lock.readLock.unlock()
        }
    }

  def writeLock[T](cb: => T): T = 
    localState.get match {
      case WRITE =>
        cb
      case _ =>
        val fallbackToRead = 
          if (localState.get == READ) {
            lock.readLock.unlock()
            true
          }
          else 
            false

        lock.writeLock.lock()
        localState.set(WRITE)

        try (cb) finally {
          if (fallbackToRead) {
            lock.readLock.lock()
            localState.set(READ)
          }
          else
            localState.set(IDLE)

          lock.writeLock.unlock()
        }
    }
}

object ReentrantReadWriteLock {
  def apply(): ReentrantReadWriteLock = 
    new ReentrantReadWriteLock
}