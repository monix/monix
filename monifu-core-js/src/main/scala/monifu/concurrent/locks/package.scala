package monifu.concurrent

/**
 * Package provided for Scala.js for source-level compatibility.
 * Usage of these locks in Scala.js does not imply any overhead.
 */
package object locks {
  type Lock = LockImpl.type
  type ReadWriteLock = ReadWriteLockImpl.type
  type NaiveReadWriteLock = ReadWriteLockImpl.type
  type NaiveSpinLock = LockImpl.type
  type ReentrantReadWriteLock = ReadWriteLockImpl.type

  def Lock(): Lock =
    LockImpl

  def ReadWriteLock(): ReadWriteLock =
    ReadWriteLockImpl

  def NaiveReadWriteLock(): NaiveReadWriteLock =
    ReadWriteLockImpl

  def NaiveSpinLock(): NaiveSpinLock =
    LockImpl

  def ReentrantReadWriteLock(): ReentrantReadWriteLock =
    ReadWriteLockImpl
}
