package monifu.concurrent.cancelables

import monifu.concurrent.Cancelable
import monifu.concurrent.atomic.Atomic

/**
 * Represents a Cancelable that can queried for the canceled status.
 */
trait BooleanCancelable extends Cancelable {
  /**
   * @return true in case this cancelable hasn't been canceled.
   */
  def isCanceled: Boolean
}

object BooleanCancelable {
  def apply(): BooleanCancelable =
    new BooleanCancelable {
      @volatile private[this] var _isCanceled = false
      def isCanceled = _isCanceled

      def cancel(): Unit = {
        _isCanceled = true
      }
    }

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

  val alreadyCanceled: BooleanCancelable =
    new BooleanCancelable {
      val isCanceled = true
      def cancel() = ()
    }
}
