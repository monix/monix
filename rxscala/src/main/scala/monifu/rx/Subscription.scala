package monifu.rx

import monifu.rx.subscriptions.BooleanSubscription

trait Subscription {
  def unsubscribe(): Unit  
}

object Subscription {
  def apply(cb: => Unit): Subscription =
    BooleanSubscription(cb)

  val empty: Subscription =
    new Subscription {
      def unsubscribe(): Unit = ()
    }
}

