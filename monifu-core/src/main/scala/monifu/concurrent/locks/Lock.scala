package monifu.concurrent.locks

import language.experimental.macros
import scala.reflect.macros.Context
import java.util.concurrent.locks.Condition
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{Lock => JavaLock}


trait Lock extends JavaLock {
  /**
   * Meant for reentrancy logic.
   *
   * @return `true` if this lock is acquired by the current thread or `false` otherwise
   */
  def isAcquiredByCurrentThread: Boolean

  /**
   * Acquires the lock.
   *
   * If the lock is not available, then the thread waits until is able to acquire the lock.
   * Does not interrupt - for interrupting see [[lockInterruptibly]] instead.
   *
   * @throws IllegalMonitorStateException in case the lock is already acquired.
   */
  final def lock(): Unit = {
    if (isAcquiredByCurrentThread)
      throw new IllegalMonitorStateException("Lock already acquired by current thread")
    unsafeLock()
  }

  /**
   * Acquires the lock. Compared to [[unsafeLock]] it doesn't do sanity checks, so used unwisely
   * it may lead to deadlocks or other undesired behavior.
   *
   * If the lock is not available, then the thread waits until is able to acquire the lock.
   * Does not interrupt - for interrupting see [[lockInterruptibly]] instead.
   */
  def unsafeLock(): Unit

  /**
   * Acquires the lock.
   *
   * If the lock is not available, then the thread waits until is able to acquire the lock.
   * Can be [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
   *
   * @throws InterruptedException in case the thread was interrupted with `Thread.interrupt()`
   * @throws IllegalMonitorStateException in case the lock is already acquired.
   */
  @throws(classOf[InterruptedException])
  final def lockInterruptibly(): Unit = {
    if (isAcquiredByCurrentThread)
      throw new IllegalMonitorStateException("Lock already acquired by current thread")
    unsafeLockInterruptibly()
  }

  /**
   * Acquires the lock. Compared to [[lockInterruptibly()]] it doesn't do any sanity checks, so used
   * unwisely it may lead to deadlocks or other undesired behavior.
   *
   * If the lock is not available, then the thread waits until is able to acquire the lock.
   * Can be [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
   *
   * @throws InterruptedException in case the thread was interrupted with `Thread.interrupt()`
   * @throws IllegalMonitorStateException in case the lock is already acquired.
   */
  def unsafeLockInterruptibly(): Unit

  /**
   * Acquires the lock only if it is free at the time of invocation.
   *
   * @throws IllegalMonitorStateException in case the lock is already acquired.
   * @return either `true` if the lock was acquired or `false` otherwise.
   */
  final def tryLock(): Boolean = {
    if (isAcquiredByCurrentThread)
      throw new IllegalMonitorStateException("Lock already acquired by current thread")
    unsafeTryLock()
  }

  /**
   * Acquires the lock only if it is free at the time of invocation. Compared to
   * [[tryLock()]] it doesn't do any sanity checks, so used
   * unwisely it may lead to deadlocks or other undesired behavior.
   *
   * @return either `true` if the lock was acquired or `false` otherwise.
   */
  def unsafeTryLock(): Boolean

  /**
   * Acquires the lock if it is free within the given waiting time and the current thread has not been
   * [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
   *
   * @param time the maximum time to wait for the lock
   * @param unit the time unit of the `time` argument
   *
   * @throws IllegalMonitorStateException    in case the lock is already acquired.
   * @throws java.lang.InterruptedException  if the current thread is interrupted while acquiring the lock
   *                                         (and interruption of lock acquisition is supported)
   *
   * @return `true` if the lock was successfully acquired or `false` if the waiting time
   *        elapsed before the lock was acquired
   */
  @throws(classOf[InterruptedException])
  final def tryLock(time: Long, unit: TimeUnit): Boolean = {
    if (isAcquiredByCurrentThread)
      throw new IllegalMonitorStateException("Lock already acquired by current thread")
    unsafeTryLock(time, unit)
  }

  /**
   * Acquires the lock if it is free within the given waiting time and the current thread has not been
   * [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
   * Compared to [[tryLock]] it doesn't do any sanity checks, so used
   * unwisely it may lead to deadlocks or other undesired behavior.
   *
   * @param time the maximum time to wait for the lock
   * @param unit the time unit of the `time` argument
   *
   * @throws java.lang.InterruptedException  if the current thread is interrupted while acquiring the lock
   *                                         (and interruption of lock acquisition is supported)
   *
   * @return `true` if the lock was successfully acquired or `false` if the waiting time
   *        elapsed before the lock was acquired
   */
  @throws(classOf[InterruptedException])
  def unsafeTryLock(time: Long, unit: TimeUnit): Boolean

  /**
   * Returns a new [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/Condition.html Condition]]
   * instance that is bound to this Lock instance.
   */
  def newCondition(): Condition

  /**
   * Releases the acquired lock.
   */
  final def unlock(): Unit = {
    if (!isAcquiredByCurrentThread)
      throw new IllegalMonitorStateException("Lock isn't acquired by current thread so cannot release")
    unsafeUnlock()
  }

  /**
   * Releases the acquired lock without doing any safety checks.
   */
  def unsafeUnlock(): Unit
}

object Lock {
  implicit class Extensions[L <: JavaLock](val self: L) extends AnyVal {
    /**
     * Executes the given callback with the lock acquired.
     *
     * Is implemented as macro to eliminate any method calls overhead.
     *
     * @example {{{
     *   private[this] val gate = Extensions()
     *   private[this] var queue = mutable.Queue.empty[Int]
     *
     *   def push(elem: Int): Unit =
     *     gate.enter {
     *       queue.enqueue(elem)
     *     }
     *
     *   def pop(): Option[Int] =
     *     gate.enter {
     *       if (queue.nonEmpty) Some(queue.dequeue()) else None
     *     }
     * }}}
     *
     * @param callback the callback to be executed once the lock is acquired
     * @return the returned value of our callback
     */
    def enter[T](callback: => T): T = macro Extensions.enterMacroImpl[L, T]

    /**
     * Executes the given callback with the lock acquired, unless the thread was
     * [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
     *
     * Is implemented as macro to eliminate any method calls overhead.
     *
     * @example {{{
     *   private[this] val gate = Extensions()
     *   private[this] var queue = mutable.Queue.empty[Int]
     *
     *   def push(elem: Int): Unit =
     *     gate.enterInterruptibly {
     *       queue.enqueue(elem)
     *     }
     *
     *   def pop(): Option[Int] =
     *     gate.enterInterruptibly {
     *       if (queue.nonEmpty) Some(queue.dequeue()) else None
     *     }
     * }}}
     *
     * @param callback the callback to be executed once the lock is acquired
     * @throws java.lang.InterruptedException exception is thrown if the thread was interrupted
     * @return the returned value of our callback
     */
    @throws(classOf[InterruptedException])
    def enterInterruptibly[T](callback: T): T = macro Extensions.enterInterruptiblyMacroImpl[L, T]

    /**
     * Acquires the lock only if it is free at the time of invocation and in case the lock was acquired then
     * executes the given callback.
     *
     * @param callback the callback to execute
     * @return either `true` in case the lock was acquired and the callback was executed, or `false` otherwise.
     */
    def tryEnter[T](callback: T): Boolean = macro Extensions.tryEnterMacro[L, T]

    /**
     * Acquires the lock only if it is free within the given waiting time and in case the lock was acquired then
     * executes the given callback.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the `time` argument
     * @param callback the callback to execute
     *
     * @return either `true` in case the lock was acquired and the callback was executed, or `false` otherwise.
     */
    def tryEnter[T](time: Long, unit: TimeUnit, callback: T): Boolean = macro Extensions.tryTimedEnterMacro[L, T]
  }

  object Extensions {
    private[this] def canProve[I : c.WeakTypeTag](c: Context) = {
      import c.universe._
      c.inferImplicitValue(weakTypeOf[I], silent=true) != EmptyTree
    }


    private[this] def getPrefix[L : c.WeakTypeTag](c: Context): c.Expr[L] = {
      import c.universe._
      c.prefix.tree match {
        case Apply(_, argument :: Nil) =>
          c.Expr[L](argument)
        case _ =>
          c.Expr[L](Select(c.prefix.tree, newTermName("self")))
      }
    }

    def enterMacroImpl[L <: JavaLock : c.WeakTypeTag, T : c.WeakTypeTag](c: Context)(callback: c.Expr[T]): c.Expr[T] = {
      import c.universe._
      val cb = c.Expr[T](c.resetLocalAttrs(callback.tree))

      if (canProve[L <:< Lock](c)) {
        val selfExpr = getPrefix[Lock](c)
        reify {
          val self = selfExpr.splice
          if (self.isAcquiredByCurrentThread)
            cb.splice
          else {
            self.unsafeLock()
            try { cb.splice } finally {
              self.unsafeUnlock()
            }
          }
        }
      }
      else {
        val selfExpr = getPrefix[JavaLock](c)
        reify {
          val self = selfExpr.splice
          self.lock()
          try { cb.splice } finally { self.unlock() }
        }
      }
    }

    def enterInterruptiblyMacroImpl[L <: JavaLock : c.WeakTypeTag, T : c.WeakTypeTag](c: Context)(callback: c.Expr[T]): c.Expr[T] = {
      import c.universe._
      val cb = c.Expr[T](c.resetLocalAttrs(callback.tree))

      if (canProve[L <:< Lock](c)) {
        val selfExpr = getPrefix[Lock](c)
        reify {
          val self = selfExpr.splice
          if (self.isAcquiredByCurrentThread)
            cb.splice
          else {
            self.unsafeLockInterruptibly()
            try {
              cb.splice
            } finally {
              self.unsafeUnlock()
            }
          }
        }
      }
      else {
        val selfExpr = getPrefix[JavaLock](c)
        reify {
          val self = selfExpr.splice
          self.lockInterruptibly()
          try {
            cb.splice
          } finally {
            self.unlock()
          }
        }
      }
    }

    def tryEnterMacro[L <: JavaLock : c.WeakTypeTag, T : c.WeakTypeTag](c: Context)(callback: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._
      val cb = c.Expr[T](c.resetLocalAttrs(callback.tree))

      if (canProve[L <:< Lock](c)) {
        val selfExpr = getPrefix[Lock](c)
        reify {
          val self = selfExpr.splice
          if (self.isAcquiredByCurrentThread) {
            cb.splice
            true
          }
          else if (self.unsafeTryLock())
            try {
              cb.splice
              true
            }
            finally {
              self.unsafeUnlock()
            }
          else
            false
        }
      }
      else {
        val selfExpr = getPrefix[JavaLock](c)
        reify {
          val self = selfExpr.splice
          if (self.tryLock())
            try {
              cb.splice
              true
            }
            finally {
              self.unlock()
            }
          else
            false
        }
      }
    }

    def tryTimedEnterMacro[L <: JavaLock : c.WeakTypeTag, T : c.WeakTypeTag](c: Context)(time: c.Expr[Long], unit: c.Expr[TimeUnit], callback: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._
      val cb = c.Expr[T](c.resetLocalAttrs(callback.tree))

      if (canProve[L <:< Lock](c)) {
        val selfExpr = getPrefix[Lock](c)
        reify {
          val self = selfExpr.splice
          if (self.isAcquiredByCurrentThread) {
            cb.splice
            true
          }
          else if (self.unsafeTryLock(time.splice, unit.splice))
            try {
              cb.splice
              true
            }
            finally {
              self.unsafeUnlock()
            }
          else
            false
        }
      }
      else {
        val selfExpr = getPrefix[JavaLock](c)
        reify {
          val self = selfExpr.splice
          if (self.tryLock(time.splice, unit.splice))
            try {
              cb.splice
              true
            }
            finally {
              self.unlock()
            }
          else
            false
        }
      }
    }
  }
}