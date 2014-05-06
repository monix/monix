package monifu.concurrent.locks

import language.experimental.macros
import scala.reflect.macros.Context
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.Date
import monifu.concurrent.misc.Unsafe

/**
 * A very efficient implementation of a `java.util.concurrent.locks.Lock` that is based on
 * [[http://en.wikipedia.org/wiki/Spinlock spinlock-ing]].
 *
 */
final class NaiveSpinLock extends java.util.concurrent.locks.Lock {
  // cache line padding
  @volatile private[this] var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16 = 10L
  @volatile private[this] var acquiringThread: Thread = null
  @volatile private[this] var s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16 = 10L

  /*
   * For keeping track of what thread acquired the lock and how many times it did it.
   * We are avoiding the use of thread-locals here to avoid boxing/unboxing, but this
   * is effectively a thread-local in usage.
   */
  private[this] var reentryCount = 0

  // using sun.misc.Unsafe for compareAndSet operations (don't do this at home)
  private[this] val unsafe = Unsafe()
  private[this] val addressOffset = NaiveSpinLock.addressOffset

  /**
   * Executes the given callback with the lock acquired.
   *
   * Is implemented as macro to eliminate any method calls overhead.
   *
   * @example {{{
   *   private[this] val gate = NaiveSpinLock()
   *   private[this] var queue = mutable.Queue.empty[Int]
   *
   *   def push(elem: Int): Unit =
   *     gate.lock {
   *       queue.enqueue(elem)
   *     }
   *
   *   def pop(): Option[Int] =
   *     gate.lock {
   *       if (queue.nonEmpty) Some(queue.dequeue()) else None
   *     }
   * }}}
   *
   * @param cb the callback to be executed once the lock is acquired
   * @return the returned value of our callback
   */
  def lock[T](cb: => T): T = macro NaiveSpinLock._lockMacroImpl[T]

  /**
   * Executes the given callback with the lock acquired, unless the thread was
   * [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
   *
   * Is implemented as macro to eliminate any method calls overhead.
   *
   * @example {{{
   *   private[this] val gate = NaiveSpinLock()
   *   private[this] var queue = mutable.Queue.empty[Int]
   *
   *   def push(elem: Int): Unit =
   *     gate.lockInterruptibly {
   *       queue.enqueue(elem)
   *     }
   *
   *   def pop(): Option[Int] =
   *     gate.lockInterruptibly {
   *       if (queue.nonEmpty) Some(queue.dequeue()) else None
   *     }
   * }}}
   *
   * @param cb the callback to be executed once the lock is acquired
   * @throws java.lang.InterruptedException exception is thrown if the thread was interrupted
   * @return the returned value of our callback
   */
  @throws(classOf[InterruptedException])
  def lockInterruptibly[T](cb: => T): T = macro NaiveSpinLock._lockInterruptiblyMacroImpl[T]

  /**
   * Acquires the lock only if it is free at the time of invocation and in case the lock was acquired then
   * executes the given callback.
   *
   * @param cb the callback to execute
   * @return either `true` in case the lock was acquired and the callback was executed, or `false` otherwise.
   */
  def tryLock[T](cb: => T): Boolean = macro NaiveSpinLock._tryLockMacroImpl[T]

  /**
   * Acquires the lock only if it is free within the given waiting time and in case the lock was acquired then
   * executes the given callback.
   *
   * @param time the maximum time to wait for the lock
   * @param unit the time unit of the `time` argument
   * @param cb the callback to execute
   *
   * @return either `true` in case the lock was acquired and the callback was executed, or `false` otherwise.
   */
  def tryLock[T](time: Long, unit: TimeUnit, cb: => T): Boolean = macro NaiveSpinLock._tryLockDurationMacroImpl[T]

  /**
   * Acquires the lock.
   *
   * If the lock is not available, then the thread waits until is able to acquire the lock.
   * Does not interrupt - for interrupting see [[lockInterruptibly]] instead.
   */
  override def lock(): Unit = {
    val currentThread = Thread.currentThread()

    // the visibility of acquiringThread is guaranteed here (volatile)
    if (acquiringThread eq currentThread) {
      // we do not care about the visibility of reentryCount, as by this point it should be only read
      // or written to by the current thread
      reentryCount += 1
    }
    else while(true) {
      if (unsafe.compareAndSwapObject(this, addressOffset, null, currentThread)) {
        // visibility of this write will happen for the acquiringThread and that's enough
        reentryCount = 0
        // we're done acquiring, return
        return
      }
    }
  }

  /**
   * Acquires the lock.
   *
   * If the lock is not available, then the thread waits until is able to acquire the lock.
   * 
   * @throws InterruptedException in case the thread was interrupted with `Thread.interrupt()`
   */
  @throws(classOf[InterruptedException])
  override def lockInterruptibly(): Unit = {
    val currentThread = Thread.currentThread()

    // the visibility of acquiringThread is guaranteed here (volatile)
    if (acquiringThread eq currentThread) {
      // we do not care about the visibility of reentryCount, as by this point it should be only read
      // or written to by the current thread
      reentryCount += 1
    }
    else while(true) {
      if (Thread.interrupted()) {
        throw new InterruptedException("NaiveSpinLock was interrupted")
      }
      else if (unsafe.compareAndSwapObject(this, addressOffset, null, currentThread)) {
        // visibility of this write will happen for the acquiringThread and that's enough
        reentryCount = 0
        // we're done acquiring, return
        return
      }
    }
  }

  /**
   * Acquires the lock only if it is free at the time of invocation. Meant to be private - using it is dangerous.
   * Use [[tryLock]] instead.
   */
  override def tryLock(): Boolean = {
    val currentThread = Thread.currentThread()

    // the visibility of acquiringThread is guaranteed here (volatile)
    if (acquiringThread eq currentThread) {
      // we do not care about the visibility of reentryCount, as by this point it should be only read
      // or written to by the current thread
      reentryCount += 1
      true
    }
    else if (unsafe.compareAndSwapObject(this, addressOffset, null, currentThread)) {
      // visibility of this write will happen for the acquiringThread and that's enough
      reentryCount = 0
      true
    }
    else
      false
  }

  /**
   * Acquires the lock if it is free within the given waiting time and the current thread has not been
   * [[http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt%28%29 interrupted]].
   *
   * @param time the maximum time to wait for the lock
   * @param unit the time unit of the `time` argument
   *
   * @throws java.lang.InterruptedException - if the current thread is interrupted while acquiring the lock
   *                                        (and interruption of lock acquisition is supported)
   *
   * @return `true` if the lock was successfully acquired or `false` if the waiting time
   *        elapsed before the lock was acquired
   */
  @throws(classOf[InterruptedException])
  override def tryLock(time: Long, unit: TimeUnit): Boolean = {
    val currentThread = Thread.currentThread()

    // the visibility of acquiringThread is guaranteed here (volatile)
    if (acquiringThread eq currentThread) {
      // we do not care about the visibility of reentryCount, as by this point it should be only read
      // or written to by the current thread
      reentryCount += 1
      true
    }
    else {
      val endsAt = System.nanoTime() + TimeUnit.NANOSECONDS.convert(time, unit)

      var isAcquired = false
      var isTimedOut = false
      var retries = 0L

      while(!isAcquired && !isTimedOut) {
        isAcquired = unsafe.compareAndSwapObject(this, addressOffset, null, currentThread)

        if (isAcquired) {
          // visibility of this write will happen for the acquiringThread and that's enough
          reentryCount = 0
        }
        else if (Thread.interrupted()) {
          throw new InterruptedException("NaiveSpinLock was interrupted")
        }
        else if (retries < 1000) {
          // only does the time checks every thousand retries because `System.nanoTime` is expensive
          retries += 1
        }
        else {
          isTimedOut = System.nanoTime() >= endsAt
          retries = 0
        }
      }

      isAcquired && !isTimedOut
    }
  }

  /**
   * Returns a new [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/Condition.html Condition]]
   * instance that is bound to this Lock instance.
   */
  def newCondition(): Condition =
    new Condition {
      private[this] val pulsar = new AnyRef

      override def await(): Unit = {
        pulsar.synchronized {
          unlock()
          pulsar.wait()
        }
        lockInterruptibly()
      }

      def awaitUninterruptibly(): Unit = {
        pulsar.synchronized {
          unlock()

          var terminated = false
          while (!terminated)
            try {
              pulsar.wait()
              terminated = true
            } catch {
              case _: InterruptedException => // ignore
            }
        }

        lock()
      }

      def awaitNanos(nanosTimeout: Long): Long = {
        val startedAt = System.nanoTime()
        val millis = TimeUnit.NANOSECONDS.toMillis(nanosTimeout)
        val remainingNanos = (nanosTimeout - TimeUnit.MILLISECONDS.toNanos(millis)).toInt

        pulsar.synchronized {
          unlock()
          pulsar.wait(millis, remainingNanos)
        }
        lockInterruptibly()
        nanosTimeout - (System.nanoTime() - startedAt)
      }

      def await(time: Long, unit: TimeUnit): Boolean =
        awaitNanos(unit.toNanos(time)) > 0

      def awaitUntil(deadline: Date): Boolean = {
        val currentTime = System.currentTimeMillis()
        val timeoutAt = deadline.getTime
        val millisToWait = timeoutAt - currentTime
        awaitNanos(TimeUnit.MILLISECONDS.toNanos(millisToWait)) > 0
      }

      def signal(): Unit =
        pulsar.synchronized(pulsar.notify())

      def signalAll(): Unit =
        pulsar.synchronized(pulsar.notifyAll())
    }


  /**
   * Releases the acquired lock.
   *
   * @throws IllegalStateException in case the lock isn't acquired by the current thread.
   */
  @throws(classOf[IllegalStateException])
  override def unlock(): Unit = {
    if (acquiringThread ne Thread.currentThread())
      throw new IllegalStateException("NaiveSpinLock cannot unlock because the lock is not acquired by the current thread")
    _unlock()
  }

  /**
   * Releases the acquired lock. Meant to be private, using it is dangerous as for
   * performance reasons it doesn't do any sanity checks.
   */
  def _unlock(): Unit = {
    // visibility is guaranteed, because it's the currentThread that incremented it from zero
    if (reentryCount != 0) {
      reentryCount -= 1
    }
    else {
      // releasing the lock, also creating a memory barrier for publishing everything modified by this thread
      acquiringThread = null
    }
  }
}

object NaiveSpinLock {
  def apply(): NaiveSpinLock =
    new NaiveSpinLock()

  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[NaiveSpinLock].getDeclaredFields.find(_.getName.endsWith("acquiringThread")).get)

  def _lockMacroImpl[T : c.WeakTypeTag](c: Context { type PrefixType = NaiveSpinLock })(cb: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    reify {
      val self = c.prefix.splice
      self.lock()
      try {
        cb.splice
      }
      finally {
        self._unlock()
      }
    }
  }

  def _lockInterruptiblyMacroImpl[T : c.WeakTypeTag](c: Context { type PrefixType = NaiveSpinLock })(cb: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    reify {
      val self = c.prefix.splice
      self.lockInterruptibly()
      try {
        cb.splice
      }
      finally {
        self._unlock()
      }
    }
  }

  def _tryLockMacroImpl[T : c.WeakTypeTag](c: Context { type PrefixType = NaiveSpinLock })(cb: c.Expr[T]): c.Expr[Boolean] = {
    import c.universe._
    reify {
      val self = c.prefix.splice
      if (self.tryLock())
        try {
          cb.splice
          true
        }
        finally {
          self._unlock()
        }
      else
        false
    }
  }

  def _tryLockDurationMacroImpl[T : c.WeakTypeTag](c: Context { type PrefixType = NaiveSpinLock })(time: c.Expr[Long], unit: c.Expr[TimeUnit], cb: c.Expr[T]): c.Expr[Boolean] = {
    import c.universe._
    reify {
      val self = c.prefix.splice
      if (self.tryLock(time.splice, unit.splice))
        try {
          cb.splice
          true
        }
        finally {
          self._unlock()
        }
      else
        false
    }
  }
}
