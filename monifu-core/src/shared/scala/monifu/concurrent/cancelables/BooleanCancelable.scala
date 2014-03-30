package monifu.concurrent.cancelables

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Cancelable

/**
 * Represents a cancelable that can be inquired if it is canceled
 * or not.
 */
trait BooleanCancelable extends Cancelable {
  /**
   * @return true in case this cancelable hasn't been canceled.
   */
  def isCanceled: Boolean
}

object BooleanCancelable {
  def apply(cb: => Unit): BooleanCancelable =
    new BooleanCancelable {
      private[this] val _isCanceled = Atomic(false)

      def isCanceled =
        _isCanceled.get

      def cancel(): Unit =
        if (_isCanceled.compareAndSet(expect=false, update=true)) {
          cb
        }
    }

  def apply() =
    new BooleanCancelable {
      @volatile
      private[this] var b = false
      def cancel() = b = true
      def isCanceled = b
    }
}

