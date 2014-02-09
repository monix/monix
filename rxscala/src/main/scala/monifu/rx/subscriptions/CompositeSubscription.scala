package monifu.rx.subscriptions

import monifu.rx.Subscription
import scala.annotation.tailrec
import monifu.concurrent.atomic.Atomic


object CompositeSubscription {
  def apply(): CompositeSubscription =
    new CompositeSubscription()

  def apply(head: Subscription, tail: Subscription*): CompositeSubscription = {
    val cs = new CompositeSubscription()
    cs += head; for (os <- tail) cs += os
    cs
  }
}

final class CompositeSubscription private () extends BooleanSubscription {
  def isUnsubscribed =
    state.get.isUnsubscribed

  def unsubscribe() = {
    val oldState = state.getAndTransform(_.copy(Set.empty, isUnsubscribed = true))
    if (!oldState.isUnsubscribed)
      for (s <- oldState.subscriptions)
        s.unsubscribe()
  }

  @tailrec
  def add(s: Subscription): Unit = {
    val oldState = state.get
    if (oldState.isUnsubscribed)
      s.unsubscribe()
    else {
      val newState = oldState.copy(subscriptions = oldState.subscriptions + s)
      if (!state.compareAndSet(oldState, newState))
        add(s)
    }
  }

  def +=(other: Subscription): Unit =
    add(other)

  @tailrec
  def remove(s: Subscription): Unit = {
    val oldState = state.get
    if (!oldState.isUnsubscribed) {
      val newState = oldState.copy(subscriptions = oldState.subscriptions - s)
      if (!state.compareAndSet(oldState, newState))
        remove(s)
    }
  }

  def -=(s: Subscription) = remove(s)

  private[this] val state = Atomic(State())

  private[this] case class State(
    subscriptions: Set[Subscription] = Set.empty,
    isUnsubscribed: Boolean = false
  )
}