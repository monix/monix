package monifu.concurrent

import monifu.concurrent.atomic.Atomic

/**
 * Represents a piece of computation, a task, that's scheduled for execution
 * by a scheduler and its execution can be cancelled.
 */
trait Cancellable {
  /**
   * Returns true, if the task has been cancelled, or false otherwise.
   */
  def isCancelled: Boolean

  /**
   * Cancels the scheduled task. No guarantees are provided for
   * what happens if a call is made concurrently with the task
   * being executed.
   */
  def cancel(): Unit
}

object Cancellable {
  def apply(cb: => Unit): Cancellable = new Cancellable {
    private[this] val _isCancelled = Atomic(false)

    def isCancelled = _isCancelled.get

    def cancel() = {
      if (_isCancelled.compareAndSet(expect=false, update=true))
        cb
    }
  }
}
