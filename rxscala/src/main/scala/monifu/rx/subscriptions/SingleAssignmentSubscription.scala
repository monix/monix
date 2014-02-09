package monifu.rx.subscriptions

import monifu.rx.Subscription
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec

object SingleAssignmentSubscription {
  def  apply(): SingleAssignmentSubscription =
    new SingleAssignmentSubscription()

  private sealed trait State
  private object State {
    case object Empty extends State
    case class Subscribed(s: Subscription) extends State
    case object Unsubscribed extends State
    case object EmptyUnsubscribed extends State
  }
}

final class SingleAssignmentSubscription private () extends BooleanSubscription {
  import SingleAssignmentSubscription.State
  import State._

  private[this] val state = Atomic(Empty : State)

  def isUnsubscribed: Boolean = state.get match {
    case EmptyUnsubscribed | Unsubscribed =>
      true
    case _ =>
      false
  }

  @tailrec
  def update(s: Subscription): Unit = state.get match {
    case Empty =>
      if (!state.compareAndSet(Empty, Subscribed(s)))
        update(s)
    case EmptyUnsubscribed =>
      if (!state.compareAndSet(EmptyUnsubscribed, Unsubscribed))
        update(s)
      else
        s.unsubscribe()
    case Unsubscribed | Subscribed(_) =>
      throw new IllegalStateException("Cannot assign to SingleAssignmentSubscription, as it was already assigned once")
  }

  @tailrec
  def unsubscribe(): Unit = state.get match {
    case Empty =>
      if (!state.compareAndSet(Empty, EmptyUnsubscribed))
        unsubscribe()
    case old @ Subscribed(s) =>
      if (!state.compareAndSet(old, Unsubscribed))
        unsubscribe()
      else
        s.unsubscribe()
    case EmptyUnsubscribed | Unsubscribed =>
      // do nothing
  }
}
