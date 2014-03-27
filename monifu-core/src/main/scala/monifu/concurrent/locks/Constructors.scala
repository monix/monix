package monifu.concurrent.locks

private[locks] trait DefaultLockConstructor {
  def apply(): Lock =
    NaiveSpinLock()
}

private[locks] trait DefaultReadWriteLockConstructor {
  def apply(): ReadWriteLock =
    NaiveReadWriteLock()
}