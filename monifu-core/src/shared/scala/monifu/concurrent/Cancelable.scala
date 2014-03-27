package monifu.concurrent

import monifu.concurrent.cancelables.BooleanCancelable

trait Cancelable {
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
