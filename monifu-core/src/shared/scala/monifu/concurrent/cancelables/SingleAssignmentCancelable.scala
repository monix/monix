package monifu.concurrent.cancelables

import monifu.concurrent.atomic.AtomicAny
import scala.annotation.tailrec
import monifu.concurrent.Cancelable


final class SingleAssignmentCancelable private () extends BooleanCancelable {
  import State._

  def isCanceled: Boolean = state.get match {
    case IsEmptyCanceled | IsCanceled =>
      true
    case _ =>
      false
  }

  @tailrec
  def update(s: Cancelable): Unit = state.get match {
    case Empty =>
      if (!state.compareAndSet(Empty, IsNotCanceled(s)))
        update(s)
    case IsEmptyCanceled =>
      if (!state.compareAndSet(IsEmptyCanceled, IsCanceled))
        update(s)
      else
        s.cancel()
    case IsCanceled | IsNotCanceled(_) =>
      throw new IllegalStateException("Cannot assign to SingleAssignmentCancelable, as it was already assigned once")
  }

  @tailrec
  def cancel(): Unit = state.get match {
    case Empty =>
      if (!state.compareAndSet(Empty, IsEmptyCanceled))
        cancel()
    case old @ IsNotCanceled(s) =>
      if (!state.compareAndSet(old, IsCanceled))
        cancel()
      else
        s.cancel()
    case IsEmptyCanceled | IsCanceled =>
    // do nothing
  }

  private[this] val state = AtomicAny(Empty : State)

  private[this] sealed trait State
  private[this] object State {
    case object Empty extends State
    case class IsNotCanceled(s: Cancelable) extends State
    case object IsCanceled extends State
    case object IsEmptyCanceled extends State
  }
}

object SingleAssignmentCancelable {
  def  apply(): SingleAssignmentCancelable =
    new SingleAssignmentCancelable()
}