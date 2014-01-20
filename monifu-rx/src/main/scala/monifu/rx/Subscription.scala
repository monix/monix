package monifu.rx

import monifu.concurrent.atomic.Atomic

trait Subscription {
  def unsubscribe(): Unit  
}

object Subscription {
  def apply(cb: => Unit): Subscription =
    BooleanSubscription(cb)
}

trait BooleanSubscription extends Subscription {
  def isUnsubscribed: Boolean
}

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

trait CompositeSubscription extends BooleanSubscription {
  def +=(other: Subscription): Unit = append(other)
  def append(other: Subscription): Unit
}

object CompositeSubscription {
  def apply(): CompositeSubscription = 
    new CompositeSubscription {
      private[this] var _unsubscribed = false
      private[this] var _subscriptions = Set.empty[Subscription]

      def isUnsubscribed = synchronized(_unsubscribed)

      def unsubscribe() = synchronized {
        _unsubscribed = true
        for (s <- _subscriptions)
          s.unsubscribe()
        _subscriptions = Set.empty
      }

      def append(other: Subscription): Unit = synchronized {
        if (!_unsubscribed)
          _subscriptions = _subscriptions + other
        else
          other.unsubscribe()
      }
    }
}
