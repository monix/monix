package monifu.concurrent

import monifu.concurrent.atomic.Atomic

/**
 * Represents an asynchronous computation whose execution can be canceled.
 * Used by [[monifu.concurrent.Scheduler]] giving you the ability to cancel scheduled units of work.
 *
 * It is equivalent to `java.io.Closeable`, but without the I/O focus, or to `IDisposable` in Microsoft .NET,
 * or to `akka.actor.Cancellable`.
 */
trait Cancelable {
  /**
   * Cancels the unit of work represented by this reference.
   *
   * Guaranteed idempotence - calling it multiple times should have the
   * same effect as calling it only a single time.
   *
   * Implementations of this method should also be thread-safe.
   */
  def cancel(): Unit
}

object Cancelable {
  def apply(cb: => Unit): Cancelable =
    new Cancelable {
      private[this] val _isCanceled = Atomic(false)

      def cancel(): Unit =
        if (_isCanceled.compareAndSet(expect=false, update=true)) {
          cb
        }
    }

  def apply(): Cancelable =
    empty

  val empty: Cancelable =
    new Cancelable {
      def cancel(): Unit = ()
    }
}
