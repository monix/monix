package monifu.rx.subscriptions

import scala.collection.mutable
import monifu.rx.Subscription
import monifu.concurrent.locks.NaiveSpinLock

object WeakCompositeSubscription {
  def apply(): WeakCompositeSubscription =
    new WeakCompositeSubscription

  def apply(s: Subscription, tail: Subscription*): WeakCompositeSubscription = {
    val sub = WeakCompositeSubscription()
    sub += s
    tail.foreach(s => sub += s)
    sub
  }
}

final class WeakCompositeSubscription private () extends BooleanSubscription {
  private[this] val map = mutable.WeakHashMap.empty[Subscription, Boolean]
  private[this] val lock = new NaiveSpinLock
  private[this] var _isUnsubscribed = false

  def isUnsubscribed: Boolean =
    lock.acquire(_isUnsubscribed)

  def +=(s: Subscription): Unit = lock.acquire {
    if (!_isUnsubscribed)
      map.update(s, true)
    else
      s.unsubscribe()
  }

  def -=(s: Subscription): Unit = lock.acquire {
    if (!_isUnsubscribed)
      map.remove(s)
  }

  def unsubscribe(): Unit = lock.acquire {
    if (!_isUnsubscribed) {
      map.keys.foreach(_.unsubscribe())
      map.clear()
      _isUnsubscribed = true
    }
  }
}
