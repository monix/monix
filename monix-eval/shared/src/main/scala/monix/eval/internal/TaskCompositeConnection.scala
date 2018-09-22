package monix.eval.internal

import cats.effect.CancelToken
import monix.eval.Task
import monix.eval.internal.TaskCompositeConnection.{Active, Cancelled, State}
import monix.execution.Scheduler
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.atomic.{Atomic, AtomicAny}
import scala.annotation.tailrec
import scala.collection.GenTraversableOnce

private[internal] final class TaskCompositeConnection private 
  (stateRef: AtomicAny[State]) {

  val cancel: CancelToken[Task] =
    Task.suspend {
      stateRef.getAndSet(Cancelled) match {
        case Cancelled => Task.unit
        case Active(set) =>
          CancelUtils.cancelAll(set.iterator)
      }
    }

  /** Adds a cancel token if the connection is still active,
    * cancels it otherwise.
    */
  @tailrec def add(other: CancelToken[Task])(implicit s: Scheduler): Unit =
    stateRef.get match {
      case Cancelled =>
        other.runAsyncAndForget
      case current @ Active(set) =>
        if (!stateRef.compareAndSet(current, Active(set + other))) {
          // $COVERAGE-OFF$
          add(other)
          // $COVERAGE-ON$
        }
    }

  /**
    * Adds a whole collection of cancellation tokens, if the
    * connection is still active, or cancels the whole collection
    * otherwise.
    */
  def addAll(that: GenTraversableOnce[CancelToken[Task]])
    (implicit s: Scheduler): Unit = {

    @tailrec def loop(that: Iterable[CancelToken[Task]]): Unit =
      stateRef.get match {
        case Cancelled =>
          CancelUtils.cancelAll(that.iterator).runAsyncAndForget
        case current @ Active(set) =>
          if (!stateRef.compareAndSet(current, Active(set ++ that))) {
            // $COVERAGE-OFF$
            loop(that)
            // $COVERAGE-ON$
          }
      }

    loop(that.toIterable.seq)
  }

  /**
    * Removes the given token reference from the underlying collection.
    */
  @tailrec def remove(token: CancelToken[Task]): Unit =
    stateRef.get match {
      case Cancelled => ()
      case current @ Active(set) =>
        if (!stateRef.compareAndSet(current, Active(set - token))) {
          // $COVERAGE-OFF$
          remove(token)
          // $COVERAGE-ON$
        }
    }
}

private[internal] object TaskCompositeConnection {
  /**
    * Builder for [[TaskCompositeConnection]].
    */
  def apply(initial: CancelToken[Task]*): TaskCompositeConnection =
    new TaskCompositeConnection(
      Atomic.withPadding(Active(Set(initial:_*)) : State, LeftRight128))

  private sealed abstract class State
  private final case class Active(set: Set[CancelToken[Task]]) extends State
  private case object Cancelled extends State
}