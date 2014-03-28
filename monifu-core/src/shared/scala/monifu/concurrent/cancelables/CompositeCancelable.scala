package monifu.concurrent.cancelables

import monifu.concurrent.atomic.{AtomicAny, Atomic}
import monifu.concurrent.Cancelable
import scala.annotation.tailrec


final class CompositeCancelable private () extends BooleanCancelable {
  def isCanceled =
    state.get.isCanceled

  def cancel() = {
    val oldState = state.getAndTransform(_.copy(Set.empty, isCanceled = true))
    if (!oldState.isCanceled)
      for (s <- oldState.subscriptions)
        s.cancel()
  }

  @tailrec
  def add(s: Cancelable): Unit = {
    val oldState = state.get
    if (oldState.isCanceled)
      s.cancel()
    else {
      val newState = oldState.copy(subscriptions = oldState.subscriptions + s)
      if (!state.compareAndSet(oldState, newState))
        add(s)
    }
  }

  def +=(other: Cancelable): Unit =
    add(other)

  @tailrec
  def remove(s: Cancelable): Unit = {
    val oldState = state.get
    if (!oldState.isCanceled) {
      val newState = oldState.copy(subscriptions = oldState.subscriptions - s)
      if (!state.compareAndSet(oldState, newState))
        remove(s)
    }
  }

  def -=(s: Cancelable) = remove(s)

  private[this] val state: AtomicAny[State] =
    Atomic(State())

  private[this] case class State(
    subscriptions: Set[Cancelable] = Set.empty,
    isCanceled: Boolean = false
  )
}

object CompositeCancelable {
  def apply(): CompositeCancelable =
    new CompositeCancelable()

  def apply(head: Cancelable, tail: Cancelable*): CompositeCancelable = {
    val cs = new CompositeCancelable()
    cs += head; for (os <- tail) cs += os
    cs
  }
}
