package monifu.rx

import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec

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


object CompositeSubscription {
  def apply(): CompositeSubscription =
    new CompositeSubscription()

  def apply(head: Subscription, tail: Subscription*): CompositeSubscription = {
    val cs = new CompositeSubscription()
    cs += head; for (os <- tail) cs += os
    cs
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

object MultiAssignmentSubscription {
  def apply(): MultiAssignmentSubscription =
    new MultiAssignmentSubscription()

  def apply(s: Subscription): MultiAssignmentSubscription = {
    val ms = new MultiAssignmentSubscription()
    ms() = s
    ms
  }
}