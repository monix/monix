package monix.execution.schedulers

import java.util.concurrent.ThreadFactory

import monix.execution.atomic.AtomicLong

private[schedulers] object ThreadFactoryBuilder {
  /**
    * Constructs a ThreadFactory using the provided name prefix and appending
    * with a unique incrementing thread identifier.
    *
    * @param name     the created threads name prefix, for easy identification.
    * @param daemonic specifies whether the created threads should be daemonic
    *                 (non-daemonic threads are blocking the JVM process on exit).
    */
  def apply(name: String, daemonic: Boolean = true): ThreadFactory = {
    new ThreadFactory {
      private[this] val threadCount = AtomicLong(0)

      def newThread(r: Runnable) = {
        val th = new Thread(r)
        th.setName(name + "-" + threadCount.incrementAndGet())
        th.setDaemon(daemonic)
        th
      }
    }
  }
}
