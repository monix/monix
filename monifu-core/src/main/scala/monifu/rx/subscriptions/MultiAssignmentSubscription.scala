package monifu.rx.subscriptions

import monifu.rx.Subscription
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec

object MultiAssignmentSubscription {
  def apply(): MultiAssignmentSubscription =
    new MultiAssignmentSubscription()

  def apply(s: Subscription): MultiAssignmentSubscription = {
    val ms = new MultiAssignmentSubscription()
    ms() = s
    ms
  }
}

final class MultiAssignmentSubscription private () extends BooleanSubscription {
  private[this] val state = Atomic(State(Subscription.empty, isUnsubscribed = false))
  private[this] case class State(subscription: Subscription, isUnsubscribed: Boolean)

  def isUnsubscribed: Boolean = state.get.isUnsubscribed

  def unsubscribe(): Unit = {
    val oldState = state.getAndTransform {
      _.copy(Subscription.empty, isUnsubscribed = true)
    }

    if (!oldState.isUnsubscribed)
      oldState.subscription.unsubscribe()
  }

  @tailrec
  def update(s: Subscription): Unit = {
    val oldState = state.get
    if (oldState.isUnsubscribed)
      s.unsubscribe()
    else {
      val newState = oldState.copy(subscription = s)
      if (!state.compareAndSet(oldState, newState))
        update(s)
    }
  }
}