package monifu.rx.subscriptions

import monifu.concurrent.atomic.Atomic
import monifu.rx.Subscription


object BooleanSubscription {
  def apply(cb: => Unit): BooleanSubscription =
    new BooleanSubscription {
      private[this] val _unsubscribed = Atomic(false)

      def isUnsubscribed =
        _unsubscribed.get

      def unsubscribe(): Unit =
        if (_unsubscribed.compareAndSet(expect=false, update=true)) {
          cb
        }
    }
}

trait BooleanSubscription extends Subscription {
  def isUnsubscribed: Boolean
}