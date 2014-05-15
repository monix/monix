package monifu.concurrent.async

import scala.concurrent.{Promise, Future}
import scala.collection.immutable.Queue
import monifu.concurrent.atomic.padded.Atomic


final class AsyncQueue[T] private (elems: T*) {
  private[this] val state = Atomic(State(Queue(elems : _*), Queue.empty))

  def poll(): Future[T] =
    state.transformAndExtract {
      case State(elements, promises) =>
        if (elements.nonEmpty) {
          val (e, newQ) = elements.dequeue
          (Future.successful(e), State(newQ, promises))
        }
        else {
          val p = Promise[T]()
          (p.future, State(elements, promises.enqueue(p)))
        }
    }

  def offer(elem: T): Unit = {
    val p = state.transformAndExtract {
      case State(elements, promises) =>
        if (promises.nonEmpty) {
          val (p, q) = promises.dequeue
          (Some(p), State(elements, q))
        }
        else
          (None, State(elements.enqueue(elem), promises))
    }

    p.foreach(_.success(elem))
  }

  private[this] case class State(elements: Queue[T], promises: Queue[Promise[T]])
}

object AsyncQueue {
  def apply[T](elems: T*) = new AsyncQueue[T](elems: _*)
  def empty[T] = new AsyncQueue[T]()
}
