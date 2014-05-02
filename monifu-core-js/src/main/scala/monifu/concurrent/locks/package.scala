package monifu.concurrent

/**
 * Package provided for Scala.js for source-level compatibility.
 * Usage of these locks in Scala.js does not imply any overhead.
 */
package object locks {
  type NaiveReadWriteLock = ReadWriteLockImpl.type
  type NaiveSpinLock = LockImpl.type

  def NaiveReadWriteLock(): NaiveReadWriteLock =
    ReadWriteLockImpl

  def NaiveSpinLock(): NaiveSpinLock =
    LockImpl
}
