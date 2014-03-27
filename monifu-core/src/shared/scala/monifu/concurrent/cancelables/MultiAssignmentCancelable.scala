package monifu.concurrent.cancelables

import monifu.concurrent.atomic.{AtomicAny, Atomic}
import scala.annotation.tailrec
import monifu.concurrent.Cancelable


final class MultiAssignmentCancelable private () extends BooleanCancelable {
  private[this] case class State(subscription: Cancelable, isCanceled: Boolean)
  private[this] val state: AtomicAny[State] =
    Atomic(State(Cancelable.empty, isCanceled = false))

  def isCanceled: Boolean = state.get.isCanceled

  def cancel(): Unit = {
    val oldState = state.getAndTransform {
      _.copy(Cancelable.empty, isCanceled = true)
    }

    if (!oldState.isCanceled)
      oldState.subscription.cancel()
  }

  @tailrec
  def update(s: Cancelable): Unit = {
    val oldState = state.get
    if (oldState.isCanceled)
      s.cancel()
    else {
      val newState = oldState.copy(subscription = s)
      if (!state.compareAndSet(oldState, newState))
        update(s)
    }
  }
}

object MultiAssignmentCancelable {
  def apply(): MultiAssignmentCancelable =
    new MultiAssignmentCancelable()

  def apply(s: Cancelable): MultiAssignmentCancelable = {
    val ms = new MultiAssignmentCancelable()
    ms() = s
    ms
  }
}