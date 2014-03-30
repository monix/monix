package monifu.concurrent

import monifu.concurrent.cancelables.BooleanCancelable

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
   */
  def cancel(): Unit
}

object Cancelable {
  def apply(cb: => Unit): Cancelable =
    BooleanCancelable(cb)

  val empty: Cancelable =
    new Cancelable {
      def cancel(): Unit = ()
    }
}
