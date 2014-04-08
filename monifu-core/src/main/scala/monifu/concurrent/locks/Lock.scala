package monifu.concurrent.locks

trait Lock {
  /**
   * Acquires a lock meant for both reading and writing.
   */
  @throws(classOf[InterruptedException])
  def acquire[T](cb: => T): T
}

trait ReadWriteLock extends Lock {
  /** 
   * Acquires a lock meant for reading. 
   */
  @throws(classOf[InterruptedException])
  def readLock[T](cb: => T): T

  /**
   * Acquires a lock meant for writing.
   */
  @throws(classOf[InterruptedException])
  def writeLock[T](cb: => T): T

  /**
   * Acquires a lock meant for both reading and writing.
   */
  final def acquire[T](cb: => T): T =
    writeLock(cb)
}

