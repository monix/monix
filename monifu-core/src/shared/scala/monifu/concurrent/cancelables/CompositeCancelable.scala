package monifu.concurrent.cancelables

import monifu.concurrent.atomic.{AtomicAny, Atomic}
import monifu.concurrent.Cancelable
import scala.annotation.tailrec


/**
 * Represents a composite of multiple cancelables. In case it is canceled, all
 * contained cancelables will be canceled too, e.g...
 * {{{
 *   val s = CompositeCancelable()
 *
 *   s += c1
 *   s += c2
 *   s += c3
 *
 *   // c1, c2, c3 will also be canceled
 *   s.cancel()
 * }}}
 *
 * Additionally, once canceled, on appending of new cancelable references, those
 * references will automatically get canceled too:
 * {{{
 *   val s = CompositeCancelable()
 *   s.cancel()
 *
 *   // c1 gets canceled, because s is already canceled
 *   s += c1
 *   // c2 gets canceled, because s is already canceled
 *   s += c2
 * }}}
 *
 * Adding and removing references from this composite is thread-safe.
 */
final class CompositeCancelable private () extends Cancelable {
  def isCanceled =
    state.get.isCanceled

  def cancel() = {
    val oldState = state.getAndTransform(_.copy(Set.empty, isCanceled = true))
    if (!oldState.isCanceled)
      for (s <- oldState.subscriptions)
        s.cancel()
  }

  /**
   * Adds a cancelable reference to this composite.
   */
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

  /**
   * Adds a cancelable reference to this composite.
   * This is an alias for `add()`.
   */
  def +=(other: Cancelable): Unit =
    add(other)

  /**
   * Removes a cancelable reference from this composite.
   */
  @tailrec
  def remove(s: Cancelable): Unit = {
    val oldState = state.get
    if (!oldState.isCanceled) {
      val newState = oldState.copy(subscriptions = oldState.subscriptions - s)
      if (!state.compareAndSet(oldState, newState))
        remove(s)
    }
  }

  /**
   * Removes a cancelable reference from this composite.
   * This is an alias for `remove()`.
   */
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
