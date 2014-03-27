package monifu.concurrent.locks

/**
 * Provides a NOOP implementation of a Lock, meant
 * for providing source compatibility in Scala.js
 */
private[locks] trait DefaultLockConstructor {
  def apply(): Lock = new Lock {
    def acquire[T](cb: => T): T =
      cb
  }
}

/**
 * Provides a NOOP implementation of a ReadWriteLock,
 * meant for providing source compatibility in Scala.js
 */
private[locks] trait DefaultReadWriteLockConstructor {
  def apply(): ReadWriteLock = new ReadWriteLock {
    def writeLock[T](cb: => T): T =
      cb

    def readLock[T](cb: => T): T =
      cb
  }
}