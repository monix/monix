package monifu.concurrent.locks

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.Date
import monifu.concurrent.misc.Unsafe

/**
 * A very efficient implementation of a `java.util.concurrent.locks.Lock` that is based on
 * [[http://en.wikipedia.org/wiki/Spinlock spinlock-ing]].
 *
 */
final class SpinLock extends Lock {
  // cache line padding
  @volatile private[this] var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16 = 10L
  @volatile private[this] var isLockAcquired: Int = 0
  @volatile private[this] var s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16 = 10L

  // using sun.misc.Unsafe for compareAndSet operations (don't do this at home)
  private[this] val unsafe = Unsafe()
  private[this] val addressOffset = SpinLock.addressOffset
  private[this] var acquiringThread: Thread = null

  override def isAcquiredByCurrentThread: Boolean =
    acquiringThread eq Thread.currentThread()

  override def unsafeLock(): Unit = {
    while (!unsafe.compareAndSwapInt(this, addressOffset, 0, 1)) {}
    acquiringThread = Thread.currentThread()
  }

  @throws(classOf[InterruptedException])
  override def unsafeLockInterruptibly(): Unit = {
    while (true) {
      if (Thread.interrupted())
        throw new InterruptedException("SpinLock was interrupted")
      else if (unsafe.compareAndSwapInt(this, addressOffset, 0, 1)) {
        acquiringThread = Thread.currentThread()
        return
      }
    }
  }

  override def unsafeTryLock(): Boolean = {
    if (unsafe.compareAndSwapInt(this, addressOffset, 0, 1)) {
      acquiringThread = Thread.currentThread()
      true
    }
    else
      false
  }

  @throws(classOf[InterruptedException])
  override def unsafeTryLock(time: Long, unit: TimeUnit): Boolean = {
    val endsAt = System.nanoTime() + TimeUnit.NANOSECONDS.convert(time, unit)

    var isAcquired = false
    var isTimedOut = false
    var retries = 0L

    while (!isAcquired && !isTimedOut) {
      isAcquired = unsafe.compareAndSwapInt(this, addressOffset, 0, 1)
      if (!isAcquired) {
        if (Thread.interrupted()) {
          throw new InterruptedException("SpinLock was interrupted")
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
      else {
        acquiringThread = Thread.currentThread()
      }
    }

    isAcquired && !isTimedOut
  }

  /**
   * Returns a new [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/Condition.html Condition]]
   * instance that is bound to this Lock instance.
   */
  def newCondition(): Condition =
    new Condition {
      private[this] val pulsar = new AnyRef

      override def await(): Unit = {
        unlock()
        try pulsar.synchronized(pulsar.wait()) finally {
          unsafeLock()
        }
      }

      def awaitUninterruptibly(): Unit = {
        unlock()
        try pulsar.synchronized {
          var terminated = false
          while (!terminated)
            try {
              pulsar.wait()
              terminated = true
            } catch {
              case _: InterruptedException => // ignore
            }
        }
        finally {
          unsafeLock()
        }
      }

      def awaitNanos(nanosTimeout: Long): Long = {
        if (!isAcquiredByCurrentThread)
          throw new IllegalMonitorStateException("Lock not currently acquired by current thread, so cannot await on condition")

        val startedAt = System.nanoTime()
        val millis = TimeUnit.NANOSECONDS.toMillis(nanosTimeout)
        val remainingNanos = (nanosTimeout - TimeUnit.MILLISECONDS.toNanos(millis)).toInt

        unlock()
        try pulsar.synchronized {
          pulsar.wait(millis, remainingNanos)
        }
        finally {
          unsafeLock()
        }
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

  def unsafeUnlock(): Unit = {
    acquiringThread = null
    isLockAcquired = 0
  }
}

object SpinLock {
  private val addressOffset =
    Unsafe.objectFieldOffset(classOf[SpinLock].getDeclaredFields.find(_.getName.endsWith("isLockAcquired")).get)
}
