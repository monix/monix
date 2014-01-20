package monifu.concurrent.locks

import monifu.concurrent.ThreadLocal
import java.util.concurrent.TimeoutException
import concurrent.duration.FiniteDuration

trait ReadWriteLock {
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
}
