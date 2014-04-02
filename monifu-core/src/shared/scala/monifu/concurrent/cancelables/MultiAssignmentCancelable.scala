package monifu.concurrent.cancelables

import monifu.concurrent.Cancelable
import monifu.concurrent.locks.NaiveSpinLock

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
  private[this] var _isCanceled = false
  private[this] var _subscription = Cancelable.empty
  private[this] val lock = NaiveSpinLock()

  def isCanceled: Boolean =
    lock.acquire(_isCanceled)

  def cancel(): Unit =
    lock.acquire {
      if (!_isCanceled)
        try _subscription.cancel() finally {
          _isCanceled = true
          _subscription = Cancelable.empty
        }
    }

  /**
   * Swaps the underlying cancelable reference with `s`.
   *
   * In case this `MultiAssignmentCancelable` is already canceled,
   * then the reference `value` will also be canceled on assignment.
   */
  def update(value: Cancelable): Unit = lock.acquire {
    if (_isCanceled)
      value.cancel()
    else
      _subscription = value
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