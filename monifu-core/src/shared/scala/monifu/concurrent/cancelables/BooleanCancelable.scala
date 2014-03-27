package monifu.concurrent.cancelables

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Cancelable

trait BooleanCancelable extends Cancelable {
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
}

