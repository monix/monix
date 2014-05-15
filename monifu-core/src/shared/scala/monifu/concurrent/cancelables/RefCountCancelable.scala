package monifu.concurrent.cancelables

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Cancelable
import scala.annotation.tailrec

/**
 * Represents a `Cancelable` that only executes the canceling logic when all
 * dependent cancelable objects have been canceled.
 *
 * After all dependent cancelables have been canceled, `onCancel` gets called.
 */
final class RefCountCancelable private (onCancel: () => Unit) extends BooleanCancelable {
  def isCanceled: Boolean =
    state.get.isCanceled

  @tailrec
  def acquireCancelable(): Cancelable = {
    val oldState = state.get
    if (oldState.isCanceled)
      Cancelable.empty
    else if (!state.compareAndSet(oldState, oldState.copy(activeCounter = oldState.activeCounter + 1)))
      acquireCancelable()
    else
      Cancelable {
        val newState = state.transformAndGet(s => s.copy(activeCounter = s.activeCounter - 1))
        if (newState.activeCounter == 0 && newState.isCanceled)
          onCancel()
      }
  }

  def cancel(): Unit = {
    val oldState = state.get
    if (!oldState.isCanceled)
      if (!state.compareAndSet(oldState, oldState.copy(isCanceled = true)))
        cancel()
      else if (oldState.activeCounter == 0)
        onCancel()
  }

  private[this] val state = Atomic(State(isCanceled = false, activeCounter = 0))
  private[this] case class State(
    isCanceled: Boolean,
    activeCounter: Int
  )
}

object RefCountCancelable {
  def apply(onCancel: => Unit): RefCountCancelable =
    new RefCountCancelable(() => onCancel)
}
