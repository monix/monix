package monifu.reactive.internals

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Cancelable
import scala.annotation.tailrec

/**
 * Represents a `Cancelable` that only executes the canceling logic when all
 * dependent cancelable objects have been canceled.
 *
 * After all dependent cancelables have been canceled, `onCancel` gets called.
 */
private[reactive] final class RefCounter private (onCancel: RefCounter => Unit) {
  import RefCounter.State
  import RefCounter.State._

  private[this] val state = Atomic(Empty : State)

  private[this] def createCancelable(): Cancelable =
    Cancelable {
      val newState = state.transformAndGet {
        case Acquired(1) =>
          Canceled
        case Acquired(n) =>
          Acquired(n - 1)
        case other =>
          throw new IllegalStateException(other.toString)
      }

      if ((newState eq Canceled) && (onCancel ne null))
        onCancel(this)
    }

  def isCanceled: Boolean =
    state.get eq Canceled

  @tailrec
  def acquire(): Cancelable = {
    state.get match {
      case Empty =>
        if (!state.compareAndSet(Empty, Acquired(1)))
          acquire()
        else
          createCancelable()
      case current @ Acquired(n) =>
        if (!state.compareAndSet(current, Acquired(n + 1)))
          acquire()
        else
          createCancelable()
      case Canceled =>
        throw new IllegalStateException("Canceled")
    }
  }
}

private[reactive] object RefCounter {
  def apply(): RefCounter =
    new RefCounter(null)

  def apply(onCancel: RefCounter => Unit): RefCounter =
    new RefCounter(onCancel)

  private sealed trait State
  private object State {
    case object Empty extends State
    case class Acquired(count: Int) extends State
    case object Canceled extends State
  }
}
