package monifu.concurrent.locks

import monifu.concurrent.ThreadLocal

trait ReadWriteLock {
  def readLock[T](cb: => T): T
  def writeLock[T](cb: => T): T
}
