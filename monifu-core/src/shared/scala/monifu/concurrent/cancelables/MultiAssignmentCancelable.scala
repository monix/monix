package monifu.concurrent.cancelables

import monifu.concurrent.atomic.{AtomicAny, Atomic}
import scala.annotation.tailrec
import monifu.concurrent.Cancelable


/**
 * Represents a [[monifu.concurrent.Cancelable]] whose underlying cancelable reference can be swapped for another.
 *
 * Example:
 * {{{
 *   val s = MultiAssignmentCancelable()
 *   s() = c1 // sets the underlying cancelable to c1
 *   s() = c2 // swaps the underlying cancelable to c2
 *
 *   s.cancel() // also cancels c2
 *
 *   s() = c3 // also cancels c3, because s is already canceled
 * }}}
 */
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

  /**
   * Swaps the underlying cancelable reference with `s`.
   *
   * In case this `MultiAssignmentCancelable` is already canceled,
   * then the reference `value` will also be canceled on assignment.
   */
  @tailrec
  def update(value: Cancelable): Unit = {
    val oldState = state.get
    if (oldState.isCanceled)
      value.cancel()
    else {
      val newState = oldState.copy(subscription = value)
      if (!state.compareAndSet(oldState, newState))
        update(value)
    }
  }

  /**
   * Alias for `update(value)`
   */
  def `:=`(value: Cancelable): Unit =
    update(value)
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