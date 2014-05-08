package monifu.concurrent.locks

import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import monifu.concurrent.atomic.padded.Atomic
import scala.annotation.tailrec

/**
 * A very efficient implementation of a `java.util.concurrent.locks.Lock` that is based on
 * [[http://en.wikipedia.org/wiki/Spinlock spinlock-ing]].
 *
 */
final class SpinLock2 extends Lock {
  private[this] val isLockAcquired = Atomic(0)
  private[this] var acquiringThread: Thread = null

  override def isAcquiredByCurrentThread: Boolean =
    acquiringThread eq Thread.currentThread()

  @tailrec
  override def unsafeLock(): Unit = {
    if (isLockAcquired.compareAndSet(0, 1))
      acquiringThread = Thread.currentThread()
    else
      unsafeLock()
  }

  @throws(classOf[InterruptedException])
  override def unsafeLockInterruptibly(): Unit = {
    isLockAcquired.waitForCompareAndSet(0, 1)
    acquiringThread = Thread.currentThread()
  }

  override def unsafeTryLock(): Boolean = {
    if (isLockAcquired.compareAndSet(0, 1)) {
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
      isAcquired = isLockAcquired.compareAndSet(0, 1)
      if (!isAcquired) {
        if (Thread.interrupted()) {
          throw new InterruptedException("SpinLock2 was interrupted")
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
    isLockAcquired.set(0)
  }
}
