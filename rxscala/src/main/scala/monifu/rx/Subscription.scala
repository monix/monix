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

  def :=(s: Subscription): Unit = update(s)

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
    ms.update(s)
    ms
  }
}

final class SingleAssignmentSubscription private () extends BooleanSubscription {
  import SingleAssignmentSubscription._
  private[this] val state = Atomic(NotInitialized : State)

  override def isUnsubscribed: Boolean =
    state.get match {
      case NotInitialized | Subscribed(_) => false
      case _ => true
    }

  @tailrec
  override def unsubscribe(): Unit = {
    val oldState = state.get
    oldState match {
      case NotInitialized =>
        if (!state.compareAndSet(NotInitialized, BlankUnsubscribed))
          unsubscribe()
      case current @ Subscribed(s) =>
        if (!state.compareAndSet(current, Unsubscribed))
          unsubscribe()
        else
          s.unsubscribe()
      case BlankUnsubscribed | Unsubscribed =>
        // do nothing
    }
  }

  @tailrec
  def update(s: Subscription): Unit = state.get match {
    case NotInitialized =>
      if (!state.compareAndSet(NotInitialized, Subscribed(s)))
        update(s)
    case BlankUnsubscribed =>
      if (!state.compareAndSet(BlankUnsubscribed, Unsubscribed))
        update(s)
      else
        s.unsubscribe()
    case Subscribed(_) | Unsubscribed =>
      throw new IllegalStateException("SingleAssignmentSubscription was already initialized")
  }

  def `:=`(s: Subscription): Unit = update(s)
}

object SingleAssignmentSubscription {
  def apply(): SingleAssignmentSubscription =
    new SingleAssignmentSubscription()

  private sealed trait State

  private case object NotInitialized extends State

  private case object Unsubscribed extends State

  private case object BlankUnsubscribed extends State

  private final case class Subscribed(s: Subscription) extends State
}