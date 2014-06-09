package monifu.concurrent

/**
 * Package provided for Scala.js for source-level compatibility.
 * Usage of these locks in Scala.js does not imply any overhead.
 */
package object locks {
  type NaiveSpinLock = LockImpl.type

  def NaiveSpinLock(): NaiveSpinLock =
    LockImpl
}
